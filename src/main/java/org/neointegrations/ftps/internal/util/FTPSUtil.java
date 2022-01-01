package org.neointegrations.ftps.internal.util;

import org.apache.commons.net.ftp.FTPFile;
import org.mule.extension.file.common.api.matcher.FileMatcher;
import org.mule.extension.file.common.api.matcher.NullFilePayloadPredicate;
import org.mule.runtime.api.connection.ConnectionException;
import org.neointegrations.ftps.internal.FTPSConnection;
import org.neointegrations.ftps.api.FTPSFileAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;

public class FTPSUtil {

    private static final Logger _logger = LoggerFactory.getLogger(FTPSUtil.class);
    private final static DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("YYYYMMddhhmmssSSS");
    private final static String TIME_STAMP_DEFAULT_STR = "TS";

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

    public static void deleteRecursive(String targetFolder, FTPSConnection connection)
            throws IOException, ConnectionException {

        FTPFile[] folders = connection.ftpsClient().listDirectories(targetFolder);
        FTPFile[] files = connection.ftpsClient().listFiles(targetFolder);

        // Delete all the files from current the folder
        for (FTPFile file : files) {
            if (file.getName().equals(".") || file.getName().equals("..")) {
                continue;
            }
            connection.ftpsClient().deleteFile(FTPSUtil.trimPath(targetFolder,file.getName()));
        }
        // Recursively delete files and folders from the child folders
        for (FTPFile folder : folders) {
            if (folder.getName().equals(".") || folder.getName().equals("..")) {
                continue;
            }
            // recursive
            String folderName = FTPSUtil.trimPath(targetFolder,folder.getName());
            deleteRecursive(folderName, connection);
            connection.ftpsClient().removeDirectory(folderName);
        }
    }

    public static boolean sizeCheck(FTPSConnection connection,
                                    String path, long timeBetweenSizeCheckInSeconds)
            throws IOException, InterruptedException, ConnectionException {
        connection.ftpsClient().requiredCommand();
        connection.ftpsClient().sendCommand("SIZE", path);

        String reply = connection.ftpsClient().getReplyString();
        String[] s = reply.split(" ");
        long start = -11111;
        if (s != null && s.length > 0) start = Long.valueOf(s[s.length - 1].trim());
        Thread.sleep(timeBetweenSizeCheckInSeconds * 1000);

        connection.ftpsClient().requiredCommand();
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
            if(closeable != null) closeable.close();
        } catch (Exception e) {
            _logger.warn("An exception occurred while closing {}", closeable, e);
        }
    }


    public static boolean createParentDirectory(final FTPSConnection connection, String dir)
            throws IOException, ConnectionException {

        if (dir.endsWith("/") || dir.endsWith("\\")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        String[] dirs = dir.split("[\\\\|/]");
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < dirs.length; idx++) {
            if (idx == 0) {
                if (dirs[idx] == null || "".equals(dirs[idx].trim())) {
                    // Fow unix like file systems, this will start from the root. For example "/"
                    sb.append(File.separator);
                } else {
                    // For windows, this will be the driver name, for example "c:\" or "d:\"
                    sb.append(dirs[idx].trim()).append(File.separator);
                }
                continue;
            }
            sb.append(dirs[idx]).append(File.separator);
            String cd = sb.substring(0, sb.length() - 1);
            if (!connection.ftpsClient().changeWorkingDirectory(cd)) {
                if (connection.ftpsClient().makeDirectory(cd) == false) return false;
            }
        }
        return true;
    }

    public static String makeIntermediateFileName(LocalDateTime timestamp, String fName) {
        if(fName == null) return fName;
        String tsStr = timestamp(timestamp);
        return "__" + tsStr + "_" + fName;
    }
    public static String makeIntermediateFileName(String timestamp, String fName) {
        if(fName == null) return fName;
        String tsStr = TIME_STAMP_DEFAULT_STR;
        if(timestamp != null) {
            tsStr = timestamp;
        }
        return "__" + tsStr + "_" + fName;
    }
    public static String timestamp(LocalDateTime timestamp){
        if(timestamp != null) {
            return timestamp.format(TS_FORMATTER);
        } else {
            return TIME_STAMP_DEFAULT_STR;
        }
    }

}
