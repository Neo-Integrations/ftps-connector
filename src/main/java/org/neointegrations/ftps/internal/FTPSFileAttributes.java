package org.neointegrations.ftps.internal;

import org.apache.commons.net.ftp.FTPFile;
import org.mule.extension.file.common.api.AbstractFileAttributes;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.neointegrations.ftps.internal.util.FTPSUtil;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Date;

public class FTPSFileAttributes extends AbstractFileAttributes {

    @Parameter
    private long size;

    @Parameter
    private boolean regularFile;

    @Parameter
    private boolean directory;

    @Parameter
    private boolean symbolicLink;

    @Parameter
    private String path;

    @Parameter
    private String name;

    @Parameter
    private LocalDateTime timestamp;

    private FTPFile _file;


    public FTPSFileAttributes(long size, boolean regularFile, boolean directory,
                             boolean symbolicLink, String path,
                              String name, Date timestamp, FTPFile file) {
        super(Paths.get(FTPSUtil.trimPath(path, name)));
        this.size = size;
        this.regularFile = regularFile;
        this.directory = directory;
        this.symbolicLink = symbolicLink;
        this.path = path;
        this.name = name;
        this._file = file;
        this.timestamp = asDateTime(timestamp.toInstant());
    }

    public FTPFile getFile() {
        return this._file;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setRegularFile(boolean regularFile) {
        this.regularFile = regularFile;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public void setSymbolicLink(boolean symbolicLink) {
        this.symbolicLink = symbolicLink;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public long getSize() {
        return this.size;
    }

    @Override
    public boolean isRegularFile() {
        return this.regularFile;
    }

    @Override
    public boolean isDirectory() {
        return this.directory;
    }

    @Override
    public boolean isSymbolicLink() {
        return this.symbolicLink;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    @Override
    public String getName() {
        return this.name;
    }
}
