package org.neointegrations.ftps.internal.stream;

import org.mule.runtime.api.connection.ConnectionException;
import org.neointegrations.ftps.internal.FTPSConnection;
import org.neointegrations.ftps.internal.FTPSConnectionProvider;
import org.neointegrations.ftps.internal.util.FTPSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;


public class LazyInputStream extends InputStream {

    private static final Logger _logger = LoggerFactory.getLogger(LazyInputStream.class);
    private InputStream _inputStream = null;
    private FTPSConnection _connection = null;
    private String _fileName;
    private final String _originalFileName;
    private final String _directory;
    private final boolean _deleteTheFileAfterRead;
    private FTPSConnectionProvider _provider;
    private boolean _finished = false;
    private boolean _started = false;
    private final boolean _createIntermediateFile;


    public LazyInputStream(final String directory,
                           final String fileName,
                           final boolean _deleteTheFileAfterRead,
                           final FTPSConnectionProvider provider,
                           final boolean createIntermediateFile) throws ConnectionException {

        this._directory = directory;
        this._deleteTheFileAfterRead = _deleteTheFileAfterRead;
        this._fileName = fileName;
        this._originalFileName = fileName;
        this._provider = provider;
        this._createIntermediateFile = createIntermediateFile;
    }

    @Override
    public void close() throws IOException {
        try {
            // CLose the stream
            if (_inputStream != null) {
                _inputStream.close();
            }

            if ((_started == true && _finished == true) &&
                    _deleteTheFileAfterRead == true &&
                    _connection != null) {
                // Delete the file, if
                // - the transfer has been finished successfully,
                // - the _deleteTheFileAfterRead == true and
                // - the connection object was for the file.
                try {
                    _connection.ftpsClient().completePendingCommand();
                } catch (Exception ignored) {
                }

                // Reconnect if connection was dropped
                if (!_connection.isConnected()) {
                    _connection.reconnect();
                } else {
                    FTPSUtil.requiredCommand(_connection);
                }
                String path = FTPSUtil.trimPath(_directory, this._fileName);
                _connection.ftpsClient().deleteFile(path);
            } else if ((_started == true && _finished == false) &&
                    _fileName != _originalFileName) {
                // Rename to the original file name if
                // - the file was renamed to intermediate name
                // - the transfer was started but did not finished
                renameToIntermediateOrPrevious(false);
            }
        } catch (Exception e) {
            _logger.error("Something wrong happened {}", e.getMessage(), e);
        } finally {

            if(_logger.isDebugEnabled()) {
                _logger.debug("_started={} _finished={} _deleteTheFileAfterRead={} _connection={}", _started, _finished, _deleteTheFileAfterRead, _connection);
                _logger.debug("_fileName={} _originalFileName={} ", _fileName, _originalFileName);
            }
            // reset everything
            if (_connection != null) FTPSUtil.close(_connection);
            _provider = null;
            _connection = null;
        }
    }

    @Override
    public long skip(long n) throws IOException {
        if (_inputStream == null) lazyLoadStream();
        return _inputStream.skip(n);
    }

    @Override
    public int read() throws IOException {
        if (_inputStream == null) lazyLoadStream();
        int count = this._inputStream.read();
        if (_started == false && count >= 0) _started = true;
        else if (count == -1) _finished = true;
        return count;
    }


    @Override
    public int read(byte[] b) throws IOException {
        if (_inputStream == null) lazyLoadStream();
        int count = this._inputStream.read();
        if (_started == false && count >= 0) _started = true;
        else if (count == -1) _finished = true;
        return count;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (_inputStream == null) lazyLoadStream();
        int count = this._inputStream.read(b, off, len);
        if (_started == false && count >= 0) _started = true;
        else if (count == -1) _finished = true;
        return count;
    }

    @Override
    public int available() throws IOException {
        if (_inputStream == null) lazyLoadStream();
        return _inputStream.available();
    }

    @Override
    public synchronized void reset() throws IOException {
        if (_inputStream == null) lazyLoadStream();
        _inputStream.reset();
    }

    @Override
    public synchronized void mark(int readlimit) {
        if (_inputStream == null) lazyLoadStream();
        _inputStream.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        if (_inputStream == null) lazyLoadStream();
        return _inputStream.markSupported();
    }

    private void lazyLoadStream() {
        if (_inputStream == null) {
            _inputStream = inputStream();
        }


    }

    private InputStream inputStream() {
        try {
            _connection = _provider.connect();
            _logger.info("Opening inputStream");
            if (_createIntermediateFile) {
                renameToIntermediateOrPrevious(true);
            }
            FTPSUtil.requiredCommand(_connection);
            return _connection.ftpsClient().retrieveFileStream(FTPSUtil.trimPath(_directory, _fileName));
        } catch (FileNotFoundException e) {
            _logger.error("File not found {}", _fileName, e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            _logger.error("Exception {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void renameToIntermediateOrPrevious(boolean intermediate)
            throws ConnectionException, IOException {
        String intermediateFileName = null;

        if (intermediate) {
            intermediateFileName = "__" + Calendar.getInstance().getTimeInMillis() + "_" + _fileName;
        } else {
            intermediateFileName = this._originalFileName;
        }
        _logger.info("Rename to {}", intermediateFileName);
        FTPSUtil.requiredCommand(_connection);
        _connection.ftpsClient().rename(FTPSUtil.trimPath(_directory, _fileName),
                FTPSUtil.trimPath(_directory, intermediateFileName));
        _fileName = intermediateFileName;
    }

}
