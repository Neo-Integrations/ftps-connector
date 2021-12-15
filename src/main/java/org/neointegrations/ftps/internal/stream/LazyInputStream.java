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
    private final ReentrantLock _lock = new ReentrantLock();

    private InputStream _inputStream = null;
    private FTPSConnection _connection = null;
    private String _fileName;
    private final String _directory;
    private final boolean _deleteTheFileAfterRead;
    private final FTPSConnectionProvider _provider;
    private boolean _finished = false;
    private final boolean _createIntermediateFile;


    public LazyInputStream(final String directory,
                           final String fileName,
                           final boolean _deleteTheFileAfterRead,
                           final FTPSConnectionProvider provider,
                           final boolean createIntermediateFile) throws ConnectionException {

        this._directory = directory;
        this._deleteTheFileAfterRead = _deleteTheFileAfterRead;
        this._fileName = fileName;
        this._provider = provider;
        this._createIntermediateFile = createIntermediateFile;
    }

    @Override
    public void close() throws IOException {
        try {
            // CLose the stream
            if(_inputStream != null) {
                _inputStream.close();
            }
            // Delete the file
            if(_finished && _deleteTheFileAfterRead) {

                try {_connection.getFTPSClient().completePendingCommand();}
                catch(Exception ignored) {}

                if(!_connection.isConnected()) {
                    _connection.reconnect();
                } else {
                    FTPSUtil.requiredCommand(_connection);
                }
                String path = FTPSUtil.trimPath(_directory, this._fileName);
                _connection.getFTPSClient().deleteFile(path);
            }
        } catch(Exception e) {
            _logger.error("Something wrong happened {}", e.getMessage(), e);
        } finally {
            if(_connection != null) _connection.close();
        }
    }

    @Override
    public long skip(long n) throws IOException {
        if(_inputStream == null) lazyLoadStream();
        return _inputStream.skip(n);
    }

    @Override
    public int read() throws IOException {
        if(_inputStream == null) lazyLoadStream();
        int count = this._inputStream.read();
        if(count == -1) _finished = true;
        return count;
    }


    @Override
    public int read(byte[] b) throws IOException {
        if(_inputStream == null) lazyLoadStream();
        int count = this._inputStream.read();
        if(count == -1) _finished = true;
        return count;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if(_inputStream == null) lazyLoadStream();
        int count = this._inputStream.read(b, off, len);
        if(count == -1) _finished = true;
        return count;
    }

    @Override
    public int available() throws IOException {
        if(_inputStream == null) lazyLoadStream();
        return _inputStream.available();
    }

    @Override
    public synchronized void reset() throws IOException {
        if(_inputStream == null) lazyLoadStream();
        _inputStream.reset();
    }

    @Override
    public synchronized void mark(int readlimit) {
        if(_inputStream == null) lazyLoadStream();
        _inputStream.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        if(_inputStream == null) lazyLoadStream();
        return _inputStream.markSupported();
    }

    private void lazyLoadStream() {
        if(_inputStream != null) return;

        _lock.lock();
        if(_inputStream == null) {
            _inputStream = inputStream();
        }
        _lock.unlock();
    }

    private InputStream inputStream() {
        try{
            _connection = _provider.connect();
            if(_createIntermediateFile) {
                String intermediateFileName = "__" + Calendar.getInstance().getTimeInMillis() + "_" + _fileName;
                FTPSUtil.requiredCommand(_connection);
                _connection.getFTPSClient().rename(FTPSUtil.trimPath(_directory, _fileName),
                        FTPSUtil.trimPath(_directory, intermediateFileName));
                _fileName = intermediateFileName;
            }
            FTPSUtil.requiredCommand(_connection);
            return _connection.getFTPSClient().retrieveFileStream(FTPSUtil.trimPath(_directory, _fileName));
        }  catch(FileNotFoundException e) {
            _logger.error("File not found {}", _fileName, e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            _logger.error("Exception {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

}
