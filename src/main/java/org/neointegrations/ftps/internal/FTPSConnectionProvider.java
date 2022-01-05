package org.neointegrations.ftps.internal;

import com.google.common.base.Strings;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.*;
import org.neointegrations.ftps.api.TrustStoreType;
import org.neointegrations.ftps.internal.client.FTPClientProxyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyStore;
import java.util.concurrent.locks.ReentrantLock;

import static org.mule.runtime.api.meta.model.display.PathModel.Location.EXTERNAL;
import static org.mule.runtime.api.meta.model.display.PathModel.Type.DIRECTORY;


public class FTPSConnectionProvider implements ConnectionProvider<FTPSConnection> {

    private static final Logger _logger = LoggerFactory.getLogger(FTPSConnectionProvider.class);
    private final ReentrantLock _lock = new ReentrantLock();

    @Parameter
    @Placement(tab = "General", order = 3)
    private String user;

    @Password
    @Placement(tab = "General", order = 4)
    @Parameter
    private String password;

    @Parameter
    @Placement(tab = "General", order = 1)
    private String host;

    @Optional(defaultValue = "21")
    @Placement(tab = "General", order = 2)
    @Parameter
    private int port;

    @Optional(defaultValue = "60")
    @Placement(tab = "Advanced", order = 1)
    @Parameter
    private int timeout;

    @Optional(defaultValue = "3600")
    @Placement(tab = "Advanced", order = 2)
    @Parameter
    private int socketTimeout;

    @Optional(defaultValue = "#[1024 * 8]")
    @Placement(tab = "Advanced", order = 3)
    @DisplayName("Buffer size (in bytes)")
    @Parameter
    private int bufferSizeInBytes;

    @Optional(defaultValue = "false")
    @Placement(tab = "Advanced", order = 4)
    @DisplayName("Show FTP commands")
    @Parameter
    private boolean debugFtpCommand;

    @Optional(defaultValue = "true")
    @Placement(tab = "Advanced", order = 5)
    @DisplayName("SSL session Reuse required by the FTPS server?")
    @Parameter
    private boolean sslSessionReuse;

    @Optional(defaultValue = "Europe/London")
    @Placement(tab = "Advanced", order = 6)
    @Summary("You can find all the time zones here - https://en.wikipedia.org/wiki/List_of_tz_database_time_zones")
    @DisplayName("Time zone of the server.")
    @Parameter
    private String serverTimeZone;

    @Optional(defaultValue = "true")
    @Placement(tab = "SSL Context", order = 1)
    @DisplayName("TLSv1.2 Only")
    @Parameter
    private boolean tlsV12Only;

    @Optional(defaultValue = "true")
    @Placement(tab = "SSL Context", order = 2)
    @DisplayName("Certificate validation")
    @Parameter
    private boolean enableCertificateValidation;

    @Optional()
    @Placement(tab = "SSL Context", order = 3)
    @Path(type = DIRECTORY, location = EXTERNAL)
    @DisplayName("Trust Store Path")
    @Parameter
    private String trustStorePath;

    @Optional()
    @Placement(tab = "SSL Context", order = 4)
    @DisplayName("Trust Store Password")
    @Password
    @Parameter
    private String trustStorePassword;

    @Optional()
    @Placement(tab = "SSL Context", order = 5)
    @Path(type = DIRECTORY, location = EXTERNAL)
    @DisplayName("Trust Store Type")
    @Parameter
    private TrustStoreType trustStoreType;

    @Optional()
    @Placement(tab = "SSL Context", order = 6)
    @Path(type = DIRECTORY, location = EXTERNAL)
    @DisplayName("Key Store Path")
    @Parameter
    private String keyStorePath;

    @Optional()
    @Placement(tab = "SSL Context", order = 7)
    @DisplayName("Key Store Password")
    @Password
    @Parameter
    private String keyStorePassword;

    @Optional()
    @Placement(tab = "SSL Context", order = 8)
    @DisplayName("Private Key Password")
    @Password
    @Parameter
    private String keyPassword;

    @Optional()
    @Placement(tab = "SSL Context", order = 9)
    @DisplayName("Key Alias")
    @Parameter
    private String keyAlias;

    @Optional()
    @Placement(tab = "SSL Context", order = 10)
    @Path(type = DIRECTORY, location = EXTERNAL)
    @DisplayName("Key Store Type")
    @Parameter
    private TrustStoreType keyStoreType;

    public FTPSConnectionProvider() throws ConnectionException {
        super();
        // To resolve [NET-408 Issue](https://issues.apache.org/jira/browse/NET-408), below property is needed
        // to share SSL session with the data connection
        System.setProperty("jdk.tls.useExtendedMasterSecret", "false");
    }

    @Override
    public FTPSConnection connect() throws ConnectionException {
        FTPClientProxyFactory.Builder builder = FTPClientProxyFactory.builder();
        if(builder.canIReuse()) return new FTPSConnection(this, builder.build());

        builder.withImplicit(false)
                .withSessionReuse(sslSessionReuse)
                .withpPintFtpCommand(debugFtpCommand)
                .withEnableCertificateValidation(enableCertificateValidation)
                .withServerTimeZone(serverTimeZone)
                .withUsername(user)
                .withPassword(password)
                .withHost(host)
                .withPort(port)
                .withTimeout(timeout)
                .withSocketTimeout(socketTimeout)
                .withBufferSizeInBytes(bufferSizeInBytes)
                .withTLSV12(tlsV12Only);

        if (!Strings.isNullOrEmpty(keyStorePath)) {
            if(keyStoreType == null) keyStoreType = TrustStoreType.valueOf(KeyStore.getDefaultType().toUpperCase());
            builder.withKeyStore(keyStorePath, keyStorePassword, keyPassword, keyAlias, keyStoreType.get());
        }

        if (!Strings.isNullOrEmpty(trustStorePath)) {
            if(trustStoreType == null) trustStoreType = TrustStoreType.valueOf(KeyStore.getDefaultType().toUpperCase());
            builder.withTrustStore(trustStorePath, trustStorePassword, trustStoreType.get());
        }

        return new FTPSConnection(this, builder.build());
    }

    @Override
    public void disconnect(FTPSConnection connection) {
        if (_logger.isDebugEnabled()) _logger.debug("Disconnecting...");
        if (connection == null || connection.ftpsClient() == null) return;
        connection.ftpsClient().close();
        if (_logger.isDebugEnabled()) _logger.debug("Disconnected ");
    }

    @Override
    public ConnectionValidationResult validate(final FTPSConnection connection) {
        if (_logger.isDebugEnabled()) _logger.debug("Validating connection...");
        if (connection.ftpsClient().isConnected()) {
            return ConnectionValidationResult.success();
        } else {
            return ConnectionValidationResult.failure("Connection is closed",
                    new ConnectionException("Connection is closed"));
        }
    }

    public void reconnect(final FTPSConnection connection) throws IOException, ConnectionException {
        if (_logger.isDebugEnabled()) _logger.debug("Connection re-starting...");
        connection.ftpsClient().reconnect();
        if (_logger.isDebugEnabled()) _logger.debug("Connection restarted");
    }
}
