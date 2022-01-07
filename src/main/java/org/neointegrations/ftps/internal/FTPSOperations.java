package org.neointegrations.ftps.internal;

import static org.mule.runtime.api.meta.model.display.PathModel.Location.EXTERNAL;
import static org.mule.runtime.api.meta.model.display.PathModel.Type.DIRECTORY;
import static org.mule.runtime.extension.api.annotation.param.display.Placement.ADVANCED_TAB;

import org.apache.commons.net.ftp.FTPFile;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Path;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.neointegrations.ftps.api.FTPSFileAttributes;
import org.neointegrations.ftps.api.FTPSFileMatcher;
import org.neointegrations.ftps.internal.client.FTPClientProxyFactory;
import org.neointegrations.ftps.internal.stream.LazyInputStream;
import org.neointegrations.ftps.internal.util.FTPSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;


/**
 * This class is a container for operations, every public method in this class will be taken as an extension operation.
 */
public class FTPSOperations {

    private static final Logger _logger = LoggerFactory.getLogger(FTPSOperations.class);

    @Summary("List all the files from a given directory. It is going to ignore any directory, symlinks, '.' and '..'")
    @MediaType(value = "*/*", strict = false)
    @DisplayName("List File")
    public List<Result<LazyInputStream, FTPSFileAttributes>> list(
            @Config final FTPSConfiguration ftpsConfig,
            @Connection FTPSConnection connection,
            @Optional @DisplayName("File Matching Rules")
            @Summary("Matcher to filter the listed files") FTPSFileMatcher matcher,
            @Optional(defaultValue = "false") boolean createIntermediateFile,
            @Optional(defaultValue = "true")
            @Placement(tab = ADVANCED_TAB) boolean deleteTheFileAfterRead,
            @Optional(defaultValue = "1") @Summary("Time Between size Check (in seconds)")
            @Placement(tab = ADVANCED_TAB) long timeBetweenSizeCheckInSeconds,
            @Optional(defaultValue = "true") @Summary("Enable or disable incomplete file check")
            @Placement(tab = ADVANCED_TAB) boolean sizeCheckEnabled,
            @Path(type = DIRECTORY, location = EXTERNAL)
            @Optional(defaultValue = "/home/share") String sourceFolder) throws ConnectionException {

        if (_logger.isDebugEnabled()) _logger.debug("Listing a folder...");
        final List<Result<LazyInputStream, FTPSFileAttributes>> files = new ArrayList<Result<LazyInputStream, FTPSFileAttributes>>();

       if (!connection.isConnected()) {
            throw new ConnectionException("Connection is not healthy. It will be retried");
        }

        //try(FTPSConnection connection = FTPClientProxyFactory.builder().connect(conn.getProvider())) {
        try {
            final Map<String, Long> nameSizeMap = new HashMap<>();
            if (sizeCheckEnabled) {
                /* Size check of each file is required to determine if a file has
                 *   been completely written to the disk before it can be read. Below code will check
                 *   the size of each of the selected files between to query separated by timeBetweenSizeCheckInSeconds delay.
                 *   The programme will only select those files whose sizes are matched between the queries.
                 */

                // First query
                FTPFile[] listFirst = connection.ftpsClient().listFiles(sourceFolder);

                // Sleep for 2 seconds
                Thread.sleep(timeBetweenSizeCheckInSeconds * 1000);

                // Storing the data in a map
                for (FTPFile file : listFirst) {
                    if (file.getSize() >= 0)
                        nameSizeMap.put(file.getName(), file.getSize());
                    else
                        nameSizeMap.put(file.getName(), 0L);
                }
            }

            // 2nd query
            final FTPFile[] list = connection.ftpsClient().listFiles(sourceFolder);
            for (FTPFile file : list) {

                // Filters starts
                if (file == null || file.isDirectory() ||
                        (file.getName() != null && file.getName().startsWith("__"))) {
                    continue;
                }

                if (sizeCheckEnabled) {
                    Long fileSize = nameSizeMap.get(file.getName());
                    if (fileSize != null) {
                        if (fileSize == 0 && file.getSize() == 0) continue;
                        if (file.getSize() != fileSize) continue;
                    }
                }

                FTPSFileAttributes attr = new FTPSFileAttributes(file.getSize(), file.isFile(),
                        file.isDirectory(), file.isSymbolicLink(), sourceFolder,
                        file.getName(), file.getTimestamp().getTime(), file);

                Predicate<FTPSFileAttributes> match = FTPSUtil.getPredicate(matcher);
                if (!match.test(attr)) {
                    continue;
                }

                // Filters ends
                final LazyInputStream lazyStream = new LazyInputStream(sourceFolder,
                        file.getName(), deleteTheFileAfterRead, connection.getProvider(),
                        createIntermediateFile, attr.getTimestamp());

                files.add(Result.<LazyInputStream, FTPSFileAttributes>builder()
                        .output(lazyStream)
                        .attributes(attr)
                        .build());
            }
            if (_logger.isDebugEnabled()) _logger.debug("Folder listing is done...");
            return files;
        } catch (ConnectionException e) {
            _logger.error("Unable to list files {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            _logger.error("Unable to list files {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Summary("Read a file from the FTPS server.")
    @MediaType(value = "*/*", strict = false)
    @DisplayName("Read File")
    public Result<LazyInputStream, FTPSFileAttributes> read(final @Config FTPSConfiguration ftpsConfig,
                                                            final @Connection FTPSConnection connection,
                                                            final @Optional(defaultValue = "/home/share") String sourceFolder,
                                                            final @Optional(defaultValue = "abc.txt") String fileName,
                                                            @Optional(defaultValue = "false") boolean createIntermediateFile,
                                                            @Optional(defaultValue = "1") @Summary("Time Between size Check (in seconds)")
                                                            @Placement(tab = ADVANCED_TAB) long timeBetweenSizeCheckInSeconds,
                                                            @Optional(defaultValue = "true") @Summary("Enable or disable incomplete file check")
                                                            @Placement(tab = ADVANCED_TAB) boolean sizeCheckEnabled,
                                                            final @Optional(defaultValue = "true")
                                                            @Placement(tab = ADVANCED_TAB) boolean deleteFileAfterRead)
            throws IllegalStateException, ConnectionException, FileNotFoundException {

        if (_logger.isDebugEnabled()) _logger.debug("Reading the file {}", fileName);

        if (!connection.isConnected()) {
            throw new ConnectionException("Connection is not healthy. It will be retried");
        }

        //try(FTPSConnection connection = FTPClientProxyFactory.builder().connect(conn.getProvider())) {
        try {
            String path = FTPSUtil.trimPath(sourceFolder, fileName);
            String timestamp = connection.ftpsClient().getModificationTime(path);
            if (timestamp == null) {
                throw new FileNotFoundException("The file does not exists " + path);
            }

            if (sizeCheckEnabled && !connection.ftpsClient().sizeCheck(path, timeBetweenSizeCheckInSeconds)) {
                throw new IllegalStateException("The file still being written. Try after sometime again...");
            }

            FTPSFileAttributes attr = new FTPSFileAttributes(0L,
                    false, false, false, sourceFolder,
                    fileName, Calendar.getInstance().getTime(), null);

            final LazyInputStream lazyStream = new LazyInputStream(sourceFolder, fileName,
                    deleteFileAfterRead, connection.getProvider(),
                    createIntermediateFile, attr.getTimestamp());

            if (_logger.isDebugEnabled()) _logger.debug("{} file being read...", fileName);

            return Result.<LazyInputStream, FTPSFileAttributes>builder()
                    .output(lazyStream)
                    .attributes(attr)
                    .build();

        } catch (IllegalStateException exp) {
            _logger.error("Unable read file {}", exp.getMessage(), exp);
            throw exp;
        } catch (FileNotFoundException fnf) {
            _logger.error("Unable to find the file {}", fnf.getMessage(), fnf);
            throw fnf;
        } catch (Exception e) {
            _logger.error("Unable read file {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Summary("Create a new file in the FTPS server using the input content")
    @MediaType(value = "*/*", strict = false)
    @DisplayName("Write File")
    public boolean write(@Config final FTPSConfiguration ftpsConfiguration,
                         @Connection FTPSConnection connection,
                         @Optional(defaultValue = "#[payload]") InputStream sourceStream,
                         @Optional(defaultValue = "#[attributes.fileName]") String targetFileName,
                         @Optional(defaultValue = "false") boolean createIntermediateFile,
                         @Path(type = DIRECTORY, location = EXTERNAL)
                         @Optional(defaultValue = "/home/share") String targetFolder,
                         @Optional(defaultValue = "true") @Placement(tab = ADVANCED_TAB) boolean overwriteFile,
                         @Optional(defaultValue = "true")
                         @Placement(tab = ADVANCED_TAB) boolean createParentDirectory) throws ConnectionException {

        if (_logger.isDebugEnabled()) _logger.debug("Writing the file {}", targetFileName);
        if (!connection.isConnected()) {
            throw new ConnectionException("Connection is not healthy. It will be retried");
        }



        //try(FTPSConnection connection = FTPClientProxyFactory.builder().connect(conn.getProvider())) {
        try {
            String path = FTPSUtil.trimPath(targetFolder, targetFileName);
            String timestamp = connection.ftpsClient().getModificationTime(path);
            if (_logger.isDebugEnabled()) _logger.debug("timestamp={}", timestamp);
            if (!overwriteFile) {
                if (timestamp != null) {
                    throw new IllegalStateException("File already exist at the target location: " + path);
                }
            }
            if (createParentDirectory) {
                connection.ftpsClient().createParentDirectory(targetFolder);
            }

            boolean status = false;
            if (createIntermediateFile) {
                String intermediateFileName = "__" + Calendar.getInstance().getTimeInMillis() + "_" + targetFileName;
                String intermediatePath = FTPSUtil.trimPath(targetFolder, intermediateFileName);
                status = connection.ftpsClient().storeFile(intermediatePath, sourceStream);
                if (status) {
                    if (timestamp != null) {
                        if (_logger.isDebugEnabled()) _logger.debug("{} file deleted", path);
                        connection.ftpsClient().deleteFile(path);
                    }
                    status = connection.ftpsClient().rename(intermediatePath, path);
                }
            } else {
                if (timestamp != null) {
                    if (_logger.isDebugEnabled()) _logger.debug("{} file deleted", path);
                    connection.ftpsClient().deleteFile(path);
                }
                status = connection.ftpsClient().storeFile(path, sourceStream);
            }
            if (status) {
                if (_logger.isDebugEnabled()) _logger.debug("{} successfully created", targetFileName);
            } else {
                throw new Exception("Unable to create the file");
            }
            return true;
        } catch (IllegalStateException is) {
            throw is;
        } catch (Exception e) {
            _logger.error("Unable write file: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }

    }

    @Summary("Deleting a file from the FTPS server")
    @MediaType(value = "*/*", strict = false)
    @DisplayName("Delete File")
    public boolean rmFile(@Config final FTPSConfiguration ftpsConfig,
                          @Connection FTPSConnection connection,
                          @Optional(defaultValue = "#[attributes.fileName]") String targetFileName,
                          @Optional(defaultValue = "#[attributes.timestamp]")
                          @Summary("Only useful when the file was read using 'Create Intermediate file' flag on")
                                  LocalDateTime timestamp,
                          @Optional(defaultValue = "true") @Placement(tab = ADVANCED_TAB)
                                  boolean ignoreErrorWhenFileNotPresent,
                          @Path(type = DIRECTORY, location = EXTERNAL)
                          @Optional(defaultValue = "/home/share") String targetFolder) throws RuntimeException, FileNotFoundException, ConnectionException {

        if (_logger.isDebugEnabled()) _logger.debug("Removing the file {}", targetFileName);
        if (!connection.isConnected()) {
            throw new ConnectionException("Connection is not healthy. It will be retried");
        }
        boolean status = false;
        //try(FTPSConnection connection = FTPClientProxyFactory.builder().connect(conn.getProvider())) {
        try {
            String path = FTPSUtil.trimPath(targetFolder, targetFileName);
            String intermediatePath = FTPSUtil.trimPath(targetFolder,
                    FTPSUtil.makeIntermediateFileName(timestamp, targetFileName));

            String ts = connection.ftpsClient().getModificationTime(path);
            String tsIntermediate = connection.ftpsClient().getModificationTime(intermediatePath);
            if (ts == null && tsIntermediate == null) {
                if (!ignoreErrorWhenFileNotPresent) {
                    throw new FileNotFoundException("Unable to find the file " + path);
                }
                return false;
            }
            if (ts != null) status = connection.ftpsClient().deleteFile(path);
            else status = connection.ftpsClient().deleteFile(intermediatePath);

            if (status) {
                _logger.info("Deleted the file successfully");
            } else {
                throw new Exception("Unable to delete the file");
            }

        } catch (FileNotFoundException fnf) {
            _logger.error("File not found: {}", targetFileName, fnf);
            throw fnf;
        } catch (Exception exp) {
            _logger.error("Something went wrong while deleting the file {}", targetFileName, exp);
            throw new RuntimeException(exp.getMessage(), exp);
        }

        if (_logger.isDebugEnabled()) _logger.debug("{} file removed successfully", targetFileName);
        return status;
    }

    @Summary("Remove directory. options are there to remove the directory recursively")
    @MediaType(value = "*/*", strict = false)
    @DisplayName("Remove Directory")
    public boolean rmDir(@Config final FTPSConfiguration ftpsConfig,
                         @Connection FTPSConnection connection,
                         @Path(type = DIRECTORY, location = EXTERNAL)
                         @Optional(defaultValue = "/home/share")
                                 String targetFolder,
                         @Optional(defaultValue = "false")
                         @Summary("Please be careful while selecting this option, you might lose files/folder for good")
                                 boolean recursive,
                         @Optional(defaultValue = "true")
                         @Placement(tab = ADVANCED_TAB)
                         @Summary("Ignore the error when folder does not exists")
                                 boolean ignoreErrorWhenFolderDoesNotExists
    ) throws RuntimeException, FileNotFoundException, ConnectionException {

        if (_logger.isDebugEnabled()) _logger.debug("Removing the directory {}", targetFolder);

       if (!connection.isConnected()) {
            throw new ConnectionException("Connection is not healthy. It will be retried");
        }

        boolean status = false;
        //try(FTPSConnection connection = FTPClientProxyFactory.builder().connect(conn.getProvider())) {
        try {
            if (!connection.ftpsClient().changeWorkingDirectory(targetFolder)) {
                if (ignoreErrorWhenFolderDoesNotExists) return false;
                else throw new FileNotFoundException("Directory does not exists " + targetFolder);
            }

            if (recursive) connection.ftpsClient().deleteRecursive(targetFolder);

            if (connection.ftpsClient().removeDirectory(targetFolder))
                _logger.info("Deleted the folder successfully");
            else
                throw new Exception("Unable to delete file");

        } catch (FileNotFoundException fnf) {
            throw fnf;
        } catch (Exception exp) {
            _logger.error("Something went wrong while deleting the folder {}", targetFolder, exp);
            throw new RuntimeException("Something went wrong while deleting the folder " + targetFolder);
        }
        if (_logger.isDebugEnabled()) _logger.debug("{} successfully deleted", targetFolder);
        return true;
    }

    @Summary("Create a new directory if not already exists")
    @MediaType(value = "*/*", strict = false)
    @DisplayName("Create Directory")
    public boolean mkDir(@Config final FTPSConfiguration ftpsConfig,
                         @Connection FTPSConnection connection,
                         @Path(type = DIRECTORY, location = EXTERNAL)
                         @Optional(defaultValue = "/home/share") String targetFolder,
                         @Optional(defaultValue = "true")
                         @Placement(tab = ADVANCED_TAB) boolean createParentDirectory,
                         @Optional(defaultValue = "true") @Placement(tab = ADVANCED_TAB)
                                 boolean ignoreErrorWhenFolderExists)
            throws RuntimeException, ConnectionException {

        if (_logger.isDebugEnabled()) _logger.debug("Creating a directory {}", targetFolder);

        if (!connection.isConnected()) {
            throw new ConnectionException("Connection is not healthy. It will be retried");
        }

        boolean status = true;
        //try(FTPSConnection connection = FTPClientProxyFactory.builder().connect(conn.getProvider())) {
        try {
            if (connection.ftpsClient().changeWorkingDirectory(targetFolder)) {
                if (ignoreErrorWhenFolderExists == false) {
                    throw new IllegalStateException("Folder already exists");
                }
                return false;
            }

            if (createParentDirectory) {
                status = connection.ftpsClient().createParentDirectory(targetFolder);
            } else {
                status = connection.ftpsClient().makeDirectory(targetFolder);
            }

            if (status == false) throw new Exception("Unable to create folder");

        } catch (IllegalStateException is) {
            throw is;
        } catch (Exception exp) {
            _logger.error("Something went wrong while creating the folder {}", targetFolder, exp);
            throw new RuntimeException("Something went wrong while creating the folder " + targetFolder);
        }
        if (_logger.isDebugEnabled()) _logger.debug("Directory created {}", targetFolder);

        return true;
    }

    @Summary("Rename a file or folder in the FTP Server")
    @MediaType(value = "*/*", strict = false)
    @DisplayName("Rename")
    public boolean rename(@Config final FTPSConfiguration ftpsConfig,
                          @Connection FTPSConnection connection,
                          @Path(type = DIRECTORY, location = EXTERNAL)
                          @Optional(defaultValue = "/share/source") String sourceFolder,
                          @Optional String sourceFileName,
                          @Path(type = DIRECTORY, location = EXTERNAL)
                          @Optional(defaultValue = "/share/target") String targetFolder,
                          @Optional String targetFileName,
                          @Optional(defaultValue = "true")
                          @Placement(tab = ADVANCED_TAB) boolean createParentDirectory,
                          @Optional(defaultValue = "#[attributes.timestamp]")
                          @Summary("Only useful when the file was read using 'Create Intermediate file' flag on")
                                  LocalDateTime timestamp)
            throws ConnectionException, FileNotFoundException, RuntimeException {

        if (_logger.isDebugEnabled()) _logger.debug("Rename operation starting...");

        if (!connection.isConnected()) {
            throw new ConnectionException("Connection is not healthy. It will be retried");
        }

        boolean isFileRename = false;
        if (sourceFolder == null || targetFolder == null) {
            throw new IllegalArgumentException("Both sourceFolder and targetFolder must be provider");
        }

        if (sourceFileName != null || targetFileName != null) {
            isFileRename = true;
            if (sourceFileName == null || targetFileName == null)
                throw new IllegalArgumentException("Both sourceFileName and targetFileName requires when renaming a file");
        }

        String sourcePath = (sourceFileName != null) ? FTPSUtil.trimPath(sourceFolder, sourceFileName) : sourceFolder;
        String targetPath = (targetFileName != null) ? FTPSUtil.trimPath(targetFolder, targetFileName) : targetFolder;
        if (_logger.isDebugEnabled()) _logger.debug("Renaming the source path {} to {}", sourcePath, targetPath);
        boolean status = false;

        //try(FTPSConnection connection = FTPClientProxyFactory.builder().connect(conn.getProvider())) {
        try {

            if (isFileRename) {
                String fName = FTPSUtil.makeIntermediateFileName(timestamp, sourceFileName);
                String intermediatePath = FTPSUtil.trimPath(sourceFolder, fName);

                if (connection.ftpsClient().getModificationTime(sourcePath) == null) {
                    if (connection.ftpsClient().getModificationTime(intermediatePath) == null) {
                        throw new FileNotFoundException("The file does not exists " + sourcePath);
                    } else {
                        sourcePath = intermediatePath;
                    }
                }
            } else {
                if (!connection.ftpsClient().changeWorkingDirectory(sourcePath))
                    throw new IllegalStateException("Folder does not exists");
            }

            if (createParentDirectory) {
                connection.ftpsClient().createParentDirectory(targetFolder);
            }

            status = connection.ftpsClient().rename(sourcePath, targetPath);

            if (status == false) throw new Exception("Unable to rename");
        } catch (FileNotFoundException fnf) {
            throw fnf;
        } catch (Exception exp) {
            _logger.error("Something went wrong while renaming {} to {}", sourcePath, targetPath, exp);
            throw new RuntimeException("Something went wrong while renaming path ");
        }

        if (_logger.isDebugEnabled()) _logger.debug("Rename operation finished");
        return true;
    }
}
