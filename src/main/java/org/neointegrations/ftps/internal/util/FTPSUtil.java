package org.neointegrations.ftps.internal.util;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.mule.extension.file.common.api.matcher.FileMatcher;
import org.mule.extension.file.common.api.matcher.NullFilePayloadPredicate;
import org.neointegrations.ftps.internal.FTPSConnection;
import org.neointegrations.ftps.internal.FTPSFileAttributes;
import org.neointegrations.ftps.internal.stream.LazyInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;

public class FTPSUtil {

    private static final Logger _logger = LoggerFactory.getLogger(FTPSUtil.class);

    public static Predicate<FTPSFileAttributes> getPredicate(FileMatcher builder) {
        return (Predicate)(builder != null ? builder.build() : new NullFilePayloadPredicate());
    }

    public static String trimPath(String directory, String fileName) {
        String dir = directory;
        if (dir.endsWith("/") || dir.endsWith("\\")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        return directory + File.separator + fileName;
    }

    public static void deleteRecursive(String targetFolder, FTPSConnection connection) throws IOException {
        connection.getFTPSClient().execPROT("P");
        connection.getFTPSClient().setFileType(FTP.BINARY_FILE_TYPE);
        connection.getFTPSClient().setBufferSize(connection.getProvider().bufferSizeInBytes);
        connection.getFTPSClient().enterLocalPassiveMode();
        FTPFile[] files = connection.getFTPSClient().listFiles(targetFolder);

        if(files.length>0) {
            for (FTPFile file : files) {
                if (file.getName().startsWith(".")) {
                    continue;
                }
                String targetPath = targetFolder + File.separator + file.getName();
                if (file.isDirectory()) {
                    deleteRecursive(targetPath, connection);
                } else {
                    connection.getFTPSClient().execPROT("P");
                    connection.getFTPSClient().setFileType(FTP.BINARY_FILE_TYPE);
                    connection.getFTPSClient().setBufferSize(connection.getProvider().bufferSizeInBytes);
                    connection.getFTPSClient().enterLocalPassiveMode();
                    connection.getFTPSClient().deleteFile(targetPath);
                }
            }
        } else {
            connection.getFTPSClient().execPROT("P");
            connection.getFTPSClient().setFileType(FTP.BINARY_FILE_TYPE);
            connection.getFTPSClient().setBufferSize(connection.getProvider().bufferSizeInBytes);
            connection.getFTPSClient().enterLocalPassiveMode();
            connection.getFTPSClient().removeDirectory(targetFolder);
        }
    }

    public static void requiredCommand(FTPSConnection connection) throws IOException {
        connection.getFTPSClient().execPROT("P");
        connection.getFTPSClient().setFileType(FTP.BINARY_FILE_TYPE);
        connection.getFTPSClient().setBufferSize(connection.getProvider().bufferSizeInBytes);
        connection.getFTPSClient().enterLocalPassiveMode();
    }

    public static boolean sizeCheck(FTPSConnection connection,
                                    String path, long timeBetweenSizeCheckInSeconds) throws IOException, InterruptedException {
        FTPSUtil.requiredCommand(connection);
        connection.getFTPSClient().sendCommand("SIZE", path);

        String reply = connection.getFTPSClient().getReplyString();
        String[] s = reply.split(" ");
        long start = -11111;
        if(s != null && s.length > 0) start = Long.valueOf(s[s.length -1].trim());
        Thread.sleep(timeBetweenSizeCheckInSeconds * 1000);

        FTPSUtil.requiredCommand(connection);
        connection.getFTPSClient().sendCommand("SIZE", path);

        reply = connection.getFTPSClient().getReplyString();
        s = reply.split(" ");
        long end = -22222;
        if(s != null && s.length > 0) end = Long.valueOf(s[s.length-1].trim());

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
}
