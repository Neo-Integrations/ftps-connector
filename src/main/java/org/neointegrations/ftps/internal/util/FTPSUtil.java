package org.neointegrations.ftps.internal.util;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.mule.extension.file.common.api.matcher.FileMatcher;
import org.mule.extension.file.common.api.matcher.NullFilePayloadPredicate;
import org.mule.runtime.api.connection.ConnectionException;
import org.neointegrations.ftps.internal.FTPSConnection;
import org.neointegrations.ftps.api.FTPSFileAttributes;
import org.neointegrations.ftps.internal.client.MuleFTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;

public class FTPSUtil {

    private static final Logger _logger = LoggerFactory.getLogger(FTPSUtil.class);

    public static Predicate<FTPSFileAttributes> getPredicate(FileMatcher builder) {
        return (Predicate) (builder != null ? builder.build() : new NullFilePayloadPredicate());
    }

    public static String trimPath(String directory, String fileName) {
        String dir = directory;
        if (dir.endsWith("/") || dir.endsWith("\\")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        return directory + File.separator + fileName;
    }

    public static void deleteRecursive(String targetFolder, FTPSConnection connection) throws IOException, ConnectionException {
        requiredCommand(connection);
        FTPFile[] files = connection.ftpsClient().listFiles(targetFolder);

        if (files.length > 0) {
            for (FTPFile file : files) {
                if (file.getName().startsWith(".")) {
                    continue;
                }
                String targetPath = targetFolder + File.separator + file.getName();
                if (file.isDirectory()) {
                    deleteRecursive(targetPath, connection);
                } else {
                    requiredCommand(connection);
                    connection.ftpsClient().deleteFile(targetPath);
                }
            }
        } else {
            requiredCommand(connection);
            connection.ftpsClient().removeDirectory(targetFolder);
        }
    }

    public static void requiredCommand(FTPSConnection connection) throws ConnectionException {
        try {
            connection.ftpsClient().execPROT("P");
            connection.ftpsClient().setFileType(FTP.BINARY_FILE_TYPE);
            connection.ftpsClient().setBufferSize(connection.getProvider().bufferSizeInBytes);
            connection.ftpsClient().enterLocalPassiveMode();
        } catch (FTPConnectionClosedException exp) {
            connection.reconnect();
        } catch (IOException exp) {
            throw new ConnectionException(exp);
        }

    }

    public static boolean sizeCheck(FTPSConnection connection,
                                    String path, long timeBetweenSizeCheckInSeconds)
            throws IOException, InterruptedException, ConnectionException {
        FTPSUtil.requiredCommand(connection);
        connection.ftpsClient().sendCommand("SIZE", path);

        String reply = connection.ftpsClient().getReplyString();
        String[] s = reply.split(" ");
        long start = -11111;
        if (s != null && s.length > 0) start = Long.valueOf(s[s.length - 1].trim());
        Thread.sleep(timeBetweenSizeCheckInSeconds * 1000);

        FTPSUtil.requiredCommand(connection);
        connection.ftpsClient().sendCommand("SIZE", path);

        reply = connection.ftpsClient().getReplyString();
        s = reply.split(" ");
        long end = -22222;
        if (s != null && s.length > 0) end = Long.valueOf(s[s.length - 1].trim());

        if (start == 0 && end == 0) return false;
        else return start == end;

    }

    public static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            _logger.warn("An exception occurred while closing {}", closeable, e);
        }
    }

    public static void logoutQuietly(final MuleFTPSClient client ) {
        try {
            if (client.isAvailable()) {
                client.logout();
            }
        } catch (Exception e) {
            _logger.warn("An exception occurred while logout {}",e.getMessage(), e);
        }
    }
    public static void disconnectQuietly(final MuleFTPSClient client ) {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (Exception e) {
            _logger.warn("An exception occurred while disconnecting {}",e.getMessage(), e);
        }
    }

    public static FTPFile[] listFiles(FTPSConnection connection, String sourceFolder)
            throws IOException, ConnectionException {
        FTPFile[] list = null;
        try {
            FTPSUtil.requiredCommand(connection);
            if(_logger.isDebugEnabled()) _logger.debug("Listing: {}", sourceFolder);
            list = connection.ftpsClient().listFiles(sourceFolder);
        } catch (IOException io) {
            _logger.error("Exception while listening folder. {}", io.getMessage());
            if ("Invalid SSL Session".equals(io.getMessage())) {
                if(connection.ftpsClient().isConnected()) {
                    connection.ftpsClient().disconnect();
                }
                connection.reconnect();
                list = connection.ftpsClient().listFiles(sourceFolder);
            } else {
                throw io;
            }
        }
        int reply = connection.ftpsClient().getReplyCode();
        if(_logger.isDebugEnabled()) _logger.debug("ReplyCode: {}", reply);
        if (FTPReply.isNegativePermanent(connection.ftpsClient().getReplyCode())) {
            _logger.error("File / Folder does not exists. ReplyCode={}, Folder={}", reply, sourceFolder);
            throw new RuntimeException("File / Folder does not exists");
        }
        return list;
    }
}
