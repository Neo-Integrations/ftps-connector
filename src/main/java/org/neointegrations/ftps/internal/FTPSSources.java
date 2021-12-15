package org.neointegrations.ftps.internal;

import org.mule.extension.file.common.api.matcher.NullFilePayloadPredicate;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.execution.OnError;
import org.mule.runtime.extension.api.annotation.execution.OnSuccess;
import org.mule.runtime.extension.api.annotation.execution.OnTerminate;
import org.mule.runtime.extension.api.annotation.param.*;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Path;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.annotation.source.ClusterSupport;
import org.mule.runtime.extension.api.annotation.source.SourceClusterSupport;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.source.PollContext;
import org.mule.runtime.extension.api.runtime.source.PollingSource;
import org.mule.runtime.extension.api.runtime.source.SourceCallbackContext;
import org.neointegrations.ftps.internal.stream.LazyInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Predicate;

import static java.lang.String.format;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.meta.model.display.PathModel.Location.EXTERNAL;
import static org.mule.runtime.api.meta.model.display.PathModel.Type.DIRECTORY;
import static org.mule.runtime.core.api.util.ExceptionUtils.extractConnectionException;
import static org.mule.runtime.core.api.util.IOUtils.closeQuietly;
import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;
import static org.mule.runtime.extension.api.annotation.param.display.Placement.ADVANCED_TAB;
import static org.mule.runtime.extension.api.runtime.source.PollContext.PollItemStatus.SOURCE_STOPPING;

@MediaType(value = ANY, strict = false)
@DisplayName("On New or Updated File")
@Summary("When a new file created / updated in the directory")
@Alias("ftps-listener")
@ClusterSupport(SourceClusterSupport.DEFAULT_PRIMARY_NODE_ONLY)
public class FTPSSources extends PollingSource<InputStream, FTPSFileAttributes> {

    private static final Logger _logger = LoggerFactory.getLogger(FTPSSources.class);

    @Config
    private FTPSConfiguration config;

    @Connection
    private ConnectionProvider<FTPSConnection> _connectionProvider;

    @Parameter
    @Optional
    @DisplayName("File Matching Rules")
    @Summary("Matcher to filter the listed files")
    private FTPSFileMatcher predicateBuilder;

    @Parameter
    @Optional(defaultValue = "1")
    @Summary("Time Between size Check (in seconds)")
    @Placement(tab = ADVANCED_TAB)
    private long timeBetweenSizeCheckInSeconds;

    @Parameter
    @Optional(defaultValue = "true")
    @Summary("Enable or disable incomplete file check")
    @Placement(tab = ADVANCED_TAB)
    private boolean sizeCheckEnabled;

    @Parameter
    @Path(type = DIRECTORY, location = EXTERNAL)
    @Optional(defaultValue = "/home/share")
    private String sourceFolder;

    @Parameter
    @Optional(defaultValue = "false")
    private boolean createIntermediateFile;


    @Parameter
    @Optional(defaultValue = "false")
    private boolean watermarkEnabled;

    private Predicate<FTPSFileAttributes> matcher;


    @Override
    protected void doStart() {
        refreshMatcher();
    }

    @Override
    public void poll(PollContext<InputStream, FTPSFileAttributes> pollContext) {
        _logger.info("*** poll");
        refreshMatcher();
        FTPSConnection connection = null;
        if (!pollContext.isSourceStopping()) {
            try {
                connection  = _connectionProvider.connect();
                final FTPSOperations read = new FTPSOperations();
                final List<Result<LazyInputStream, FTPSFileAttributes>> files =
                        read.list( config, connection, predicateBuilder,
                                createIntermediateFile,false,
                                timeBetweenSizeCheckInSeconds, sizeCheckEnabled, sourceFolder);

                for (Result<LazyInputStream, FTPSFileAttributes> file : files) {
                    if (pollContext.isSourceStopping() || !process(pollContext, file)) {
                        break;
                    }
                }
            } catch (Exception e) {
                _logger.error("Found exception trying to poll directory '{}'. Will try again on the next poll. Error message: {}",
                        sourceFolder, e.getMessage(), e);
                extractConnectionException(e).ifPresent(pollContext::onConnectionException);
            } finally {
                try{
                    connection.close();
                } catch(Exception ignored) {}
            }

        }
    }


    @OnSuccess
    public void onSuccess(@ParameterGroup(name = "Post processing action") PostActionGroup postAction,
                        SourceCallbackContext ctx) {
        _logger.info("*** onSuccess");

        ctx.<FTPSFileAttributes>getVariable("attributes").ifPresent(attrs -> {
            FTPSConnection connection = null;
            try {
                connection  = _connectionProvider.connect();

                _logger.info("*** postAction.isAutoDelete(): {}", postAction.isAutoDelete());
                if(postAction.isAutoDelete()) {
                    connection.getFTPSClient().deleteFile(attrs.getPath() + File.separator + attrs.getName());
                }

                _logger.info("*** postAction.getRenameTo(): {}", postAction.getRenameTo());
                if(postAction.getRenameTo() != null) {
                    connection.getFTPSClient().rename(attrs.getPath() + File.separator + attrs.getName(),
                            attrs.getPath() + File.separator + postAction.getRenameTo());
                }

                if(postAction.getMoveToDirectory() != null) {
                    connection.getFTPSClient().rename(attrs.getPath() + File.separator + attrs.getName(),
                            postAction.getMoveToDirectory() + File.separator + attrs.getName());
                }

            } catch (ConnectionException e){
                _logger.error("An error occurred while retrieving a connection to apply the post processing action to the file {}, it was neither moved nor deleted.",
                        attrs.getPath() + '/' + attrs.getName(), e);
            } catch (IOException e) {
                _logger.error("An error occurred while retrieving a connection to apply the post processing action to the file {}, it was neither moved nor deleted.",
                        attrs.getPath() + '/' + attrs.getName(), e);
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        });
    }


    @OnError
    public void onError(@ParameterGroup(name = "Post processing action") PostActionGroup postAction,
                        SourceCallbackContext ctx) throws ConnectionException{
        _logger.info("*** onError");
        if(postAction.isApplyPostActionWhenFailed()) {
            ctx.<FTPSFileAttributes>getVariable("attributes").ifPresent(attrs -> {
                FTPSConnection connection = null;

                try {
                    connection  = _connectionProvider.connect();

                    if(postAction.isAutoDelete()) {
                        connection.getFTPSClient().deleteFile(attrs.getPath() + File.separator + attrs.getName());
                    }

                    if(postAction.getRenameTo() != null) {
                        connection.getFTPSClient().rename(attrs.getPath() + File.separator + attrs.getName(),
                                attrs.getPath() + File.separator + postAction.getRenameTo());
                    }

                    if(postAction.getMoveToDirectory() != null) {
                        connection.getFTPSClient().rename(attrs.getPath() + File.separator + attrs.getName(),
                                postAction.getMoveToDirectory() + File.separator + attrs.getName());
                    }

                } catch (ConnectionException e){
                    _logger.error("An error occurred while retrieving a connection to apply the post processing action to the file {}, it was neither moved nor deleted.",
                            attrs.getPath() + '/' + attrs.getName(), e);
                } catch (IOException e) {
                    _logger.error("An error occurred while retrieving a connection to apply the post processing action to the file {}, it was neither moved nor deleted.",
                            attrs.getPath() + '/' + attrs.getName(), e);
                } finally {
                    if (connection != null) {
                        connection.close();
                    }
                }
            });
        }
    }

    @Override
    protected void doStop() {

    }

    @OnTerminate
    public void onTerminate(SourceCallbackContext ctx) {
        //Does nothing
    }


    @Override
    public void onRejectedItem(Result<InputStream, FTPSFileAttributes> result,
                               SourceCallbackContext callbackContext) {
        closeQuietly(result.getOutput());
    }

    private boolean process(PollContext<InputStream,
            FTPSFileAttributes> pollContext,
                            Result<? extends InputStream, FTPSFileAttributes> file) {
        boolean result = true;
        FTPSFileAttributes attributes = file.getAttributes()
                .orElseThrow(() -> new MuleRuntimeException(createStaticMessage("Could not process file: attributes not available")));
        if (attributes.isRegularFile()) {
            result = processFile(file, attributes, pollContext);
        }
        return result;
    }

    private boolean processFile(Result<? extends InputStream, FTPSFileAttributes> file, FTPSFileAttributes attributes,
                                PollContext<InputStream, FTPSFileAttributes> pollContext) {

        PollContext.PollItemStatus status = pollContext.accept( (item) -> {
            final SourceCallbackContext ctx = item.getSourceCallbackContext();

            try {
                ctx.addVariable("attributes", attributes);
                item.setResult((Result<InputStream, FTPSFileAttributes>)file)
                        .setId(attributes.getPath() + File.separator + attributes.getName());

                if (watermarkEnabled) {
                    item.setWatermark(attributes.getTimestamp());
                }
            } catch (Exception t) {
                closeQuietly(file.getOutput());
                throw new MuleRuntimeException(createStaticMessage(format("Found file '%s' but found exception trying to dispatch it for processing.",
                        attributes.getPath())),t);
            }
        });
        boolean result = status != SOURCE_STOPPING;
        if (!result) {
            closeQuietly(file.getOutput());
        }
        return result;
    }


    private void refreshMatcher() {
        matcher = predicateBuilder != null ? predicateBuilder.build() : new NullFilePayloadPredicate<>();
    }
}
