package org.neointegrations.ftps.internal.client;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.*;
import org.mule.runtime.api.connection.ConnectionException;
import org.neointegrations.ftps.internal.util.FTPSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

public class FTPSClientProxy implements AutoCloseable {
    private static final Logger _logger = LoggerFactory.getLogger(FTPSClientProxy.class);
    private final boolean _isImplicit;
    private final SSLContext _sslContext;
    private boolean _sessionReuse = false;
    private  FTPSClient _client;
    private final boolean _debugFtpCommand;
    private final boolean _remoteVerificationEnable;
    private final String _serverTimeZone;
    private final String _user;
    private final String _password;
    private final String _host;
    private final int _port;
    private final int _timeout;
    private final int _socketTimeout;
    private final int _bufferSizeInBytes;

    public FTPSClientProxy(final boolean isImplicit,
                           final SSLContext sslContext,
                           final boolean sessionReuse,
                           final boolean debugFtpCommand,
                           final boolean remoteVerificationEnable,
                           final String serverTimeZone,
                           final String user,
                           final String password,
                           final String host,
                           final int port,
                           final int timeout,
                           final int socketTimeout,
                           final int bufferSizeInBytes) throws ConnectionException {
       this._isImplicit = isImplicit;
       this._sslContext = sslContext;
       this._sessionReuse = sessionReuse;
       this._debugFtpCommand = debugFtpCommand;
       this._remoteVerificationEnable = remoteVerificationEnable;
       this._serverTimeZone = serverTimeZone;
       this._user = user;
       this._password = password;
       this._host = host;
       this._port = port;
       this._timeout = timeout;
       this._socketTimeout = socketTimeout;
       this._bufferSizeInBytes = bufferSizeInBytes;
       this._connect_();
    }

    public boolean isAvailable() {
        return _client.isAvailable();
    }

    public boolean isConnected() {
        return _client.isConnected();
    }

    public int sendCommand(String command, String path) throws IOException {
        return _client.sendCommand(command, path);
    }
    public String getReplyString() {
        return _client.getReplyString();
    }

    public void requiredCommand() throws ConnectionException {
        try {
            _client.execPBSZ(0);
            _client.execPROT("P");
            _client.setFileType(FTP.BINARY_FILE_TYPE);
            _client.setBufferSize(_bufferSizeInBytes);
            _client.enterLocalPassiveMode();
        } catch (FTPConnectionClosedException exp) {
            this.reconnect();
        } catch (IOException exp) {
            throw new ConnectionException(exp);
        }
    }
    public void reconnect() throws ConnectionException {
        this.close();
        this._connect_();
    }
    private void _connect_() throws ConnectionException {

        _client = new FTPSClient(_isImplicit, _sslContext, _sessionReuse);
        try {
            if (_debugFtpCommand) {
                _client.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out),
                        true));
            }
            _client.setRemoteVerificationEnabled(_remoteVerificationEnable);

            final FTPClientConfig config = new FTPClientConfig();
            config.setServerTimeZoneId(_serverTimeZone);
            _client.configure(config);

            // Timeout settings
            _client.setDefaultTimeout(_timeout);
            _client.setConnectTimeout(_timeout);

            _client.connect(_host, _port);

            _client.setSoTimeout(_socketTimeout);
            _client.login(_user, _password);
            _client.enterLocalPassiveMode();

            if (_logger.isDebugEnabled()) _logger.debug("Connected to {}", _host);
            if (_logger.isDebugEnabled()) _logger.debug("Reply: {}", _client.getReplyString());
            int reply = _client.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                _client.disconnect();
                _logger.error("FTP server refused connection with reply {}", reply);
                throw new ConnectionException("FTP server refused connection");
            }
            this.requiredCommand();
            if (_logger.isDebugEnabled()) _logger.debug("Connection started");
        } catch (IOException e) {
            _logger.error("FTPS server refused connection", e);
            this.logoutQuietly();
            try {
                _client.disconnect();
            } catch (Exception exp) {
                throw new RuntimeException(exp);
            }
            throw new ConnectionException(e);
        }
    }

    public  void completePendingCommand() {
        try {
            if(_client != null) _client.completePendingCommand();
        } catch (Exception e) {
            _logger.warn("An exception occurred while calling completePendingCommand() {}", e.getMessage(), e);
        }
    }

    public String getModificationTime(String path) throws IOException, ConnectionException {
        try {
            this.requiredCommand();
            return _client.getModificationTime(path);
        } catch(InvalidSSLSessionException exp) {
            _logger.error("An exception occurred while calling getModificationTime {}", exp.getMessage(),  exp);
            this._connect_();
            return _client.getModificationTime(path);
        }
    }
    public boolean changeWorkingDirectory(String path)
            throws IOException, ConnectionException {
        try {
            this.requiredCommand();
            return _client.changeWorkingDirectory(path);
        } catch(InvalidSSLSessionException exp) {
            _logger.error("An exception occurred while calling changeWorkingDirectory {}", exp.getMessage(),  exp);
            this._connect_();
            return _client.changeWorkingDirectory(path);
        }
    }
    public boolean makeDirectory(String dir) throws IOException, ConnectionException {
        try {
            this.requiredCommand();
            return _client.makeDirectory(dir);
        } catch(InvalidSSLSessionException exp) {
            _logger.error("An exception occurred while calling makeDirectory {}", exp.getMessage(),  exp);
            this._connect_();
            return _client.makeDirectory(dir);
        }
    }
    public boolean rename(String sourcePath, String targetPath) throws IOException, ConnectionException {
        try {
            this.requiredCommand();
            return _client.rename(sourcePath, targetPath);
        } catch(InvalidSSLSessionException exp) {
            _logger.error("An exception occurred while calling rename {}", exp.getMessage(),  exp);
            this._connect_();
            return _client.rename(sourcePath, targetPath);
        }
    }
    public FTPFile[] listDirectories(String folder) throws IOException, ConnectionException {

        FTPFile[] list = null;
        try {
            this.requiredCommand();
            list =  _client.listDirectories(folder);
        } catch(InvalidSSLSessionException exp) {
            _logger.error("An exception occurred while calling listDirectories {}", exp.getMessage(),  exp);
            this._connect_();
            list = _client.listDirectories(folder);
        }
        int reply = _client.getReplyCode();
        if (_logger.isDebugEnabled()) _logger.debug("ReplyCode: {}", reply);
        if (FTPReply.isNegativePermanent(reply)) {
            _logger.error("Folder does not exists. ReplyCode={}, Folder={}", reply, folder);
            throw new RuntimeException("Folder does not exists");
        }
        return list;
    }

    public boolean deleteFile(String path) throws IOException, ConnectionException {
        try {
            this.requiredCommand();
            return _client.deleteFile(path);
        } catch(InvalidSSLSessionException exp) {
            _logger.error("An exception occurred while calling deleteFile {}", exp.getMessage(),  exp);
            this._connect_();
            return _client.deleteFile(path);
        }
    }
    public boolean removeDirectory(String folder) throws IOException, ConnectionException {
        try {
            this.requiredCommand();
            return _client.removeDirectory(folder);
        } catch(InvalidSSLSessionException exp) {
            _logger.error("An exception occurred while calling removeDirectory {}", exp.getMessage(),  exp);
            this._connect_();
            return _client.removeDirectory(folder);
        }
    }

    public boolean storeFile(String path, InputStream stream)
            throws IOException, ConnectionException {
        try {
            this.requiredCommand();
            return _client.storeFile(path, stream);
        } catch(InvalidSSLSessionException exp) {
            _logger.error("An exception occurred while calling storeFile {}", exp.getMessage(), exp);
            _connect_();
            _client.deleteFile(path);
            requiredCommand();
            return _client.storeFile(path, stream);
        }
    }

    public InputStream retrieveFileStream(String path) throws IOException, ConnectionException {
        InputStream is = null;
        try {
            this.requiredCommand();
            is = _client.retrieveFileStream(path);
        } catch(InvalidSSLSessionException exp) {
            _logger.error("An exception occurred while calling retrieveFileStream {}", exp.getMessage());
            this._connect_();
            is = _client.retrieveFileStream(path);
        }

        int reply = _client.getReplyCode();
        if (_logger.isDebugEnabled()) _logger.debug("ReplyCode: {}", reply);
        if (FTPReply.isNegativePermanent(reply)) {
            _logger.error("Unable to read the file. ReplyCode: {}, path: {}", reply, path);
            throw new RuntimeException("Unable to read the file");
        }
        return is;
    }

    public FTPFile[] listFiles(String sourceFolder)
            throws IOException, ConnectionException {
        FTPFile[] list = null;
        try {
            this.requiredCommand();
            if (_logger.isDebugEnabled()) _logger.debug("Listing: {}", sourceFolder);
            list = _client.listFiles(sourceFolder);
        } catch(InvalidSSLSessionException exp) {
            _logger.warn("Exception while listening folder. {}", exp.getMessage());
            this._connect_();
            list = _client.listFiles(sourceFolder);
        }

        int reply = _client.getReplyCode();
        if (_logger.isDebugEnabled()) _logger.debug("ReplyCode: {}", reply);
        if (FTPReply.isNegativePermanent(reply)) {
            _logger.error("File / Folder does not exists. ReplyCode={}, Folder={}", reply, sourceFolder);
            throw new RuntimeException("File / Folder does not exists");
        }
        return list;
    }

    public void deleteRecursive(String targetFolder)
            throws IOException, ConnectionException {

        FTPFile[] folders = this.listDirectories(targetFolder);
        FTPFile[] files = this.listFiles(targetFolder);

        // Delete all the files from current the folder
        for (FTPFile file : files) {
            if (file.getName().equals(".") || file.getName().equals("..")) {
                continue;
            }
            this.deleteFile(FTPSUtil.trimPath(targetFolder,file.getName()));
        }
        // Recursively delete files and folders from the child folders
        for (FTPFile folder : folders) {
            if (folder.getName().equals(".") || folder.getName().equals("..")) {
                continue;
            }
            // recursive
            String folderName = FTPSUtil.trimPath(targetFolder,folder.getName());
            deleteRecursive(folderName);
            this.removeDirectory(folderName);
        }
    }

    public boolean sizeCheck(String path, long timeBetweenSizeCheckInSeconds)
            throws IOException, InterruptedException, ConnectionException {

        this.requiredCommand();
        this.sendCommand("SIZE", path);

        String reply = this.getReplyString();
        String[] s = reply.split(" ");
        long start = -11111;
        if (s != null && s.length > 0) start = Long.valueOf(s[s.length - 1].trim());
        Thread.sleep(timeBetweenSizeCheckInSeconds * 1000);

        this.requiredCommand();
        this.sendCommand("SIZE", path);

        reply = this.getReplyString();
        s = reply.split(" ");
        long end = -22222;
        if (s != null && s.length > 0) end = Long.valueOf(s[s.length - 1].trim());

        if (start == 0 && end == 0) return false;
        else return start == end;

    }

    public boolean createParentDirectory(String dir)
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
            if (!this.changeWorkingDirectory(cd)) {
                if (this.makeDirectory(cd) == false) return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        logoutQuietly();
        disconnectQuietly();
    }

    private void logoutQuietly() {
        try {
            if (_client != null && _client.isAvailable()) {
                _client.logout();
            }
        } catch (Exception e) {
            _logger.warn("An exception occurred while logout {}", e.getMessage(), e);
        }
    }

    private void disconnectQuietly() {
        try {
            if (_client != null && _client.isConnected()) {
                _client.disconnect();
            }
        } catch (Exception e) {
            _logger.warn("An exception occurred while disconnecting {}", e.getMessage(), e);
        }

    }
}
