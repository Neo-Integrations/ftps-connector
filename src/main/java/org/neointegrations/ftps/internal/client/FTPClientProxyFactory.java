package org.neointegrations.ftps.internal.client;

import com.google.common.base.Strings;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.util.KeyManagerUtils;
import org.mule.runtime.api.connection.ConnectionException;
import org.neointegrations.ftps.internal.FTPSConnection;
import org.neointegrations.ftps.internal.FTPSConnectionProvider;
import org.neointegrations.ftps.internal.util.FTPSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public class FTPClientProxyFactory {
    private static final Logger _logger = LoggerFactory.getLogger(FTPClientProxyFactory.class);
    private static SSLContext _sslContext_ = null;
    private static final ReentrantLock _lock_ = new ReentrantLock();

    private static FTPClientProxyFactory.Builder _builder_;
    private static void cacheBuilder(FTPClientProxyFactory.Builder builder) {
        if(Objects.isNull(_builder_) && Objects.nonNull(builder)) {
            _lock_.lock();
            try {
                if(Objects.isNull(_builder_)) {
                    _builder_ = builder;
                }
            } finally {
                _lock_.unlock();
            }
        }
    }

    public static FTPClientProxyFactory.Builder builder() {
        if(Objects.nonNull(_builder_)) return _builder_;
        else  return new FTPClientProxyFactory.Builder();
    }

    public static class Builder {
        private boolean _isImplicit;
        private boolean _sessionReuse = false;
        private boolean _debugFtpCommand;
        private boolean _enableCertificateValidation;
        private String _serverTimeZone;
        private String _user;
        private String _password;
        private String _host;
        private int _port;
        private int _timeout;
        private int _socketTimeout;
        private int _bufferSizeInBytes;
        private String _trustStorePath;
        private String _trustStorePassword;
        private String _keyStorePath;
        private String _keyStorePassword;
        private String _keyPassword;
        private String _keyAlias;
        private String _keystoreType;
        private String _trustStoreType;
        private boolean _tlsV12Only;
        private boolean _sslContextCache;
        private Builder INSTANCE = null;

        private Builder() {
            this._isImplicit = false;
            this._tlsV12Only = false;
            this._sessionReuse = true;
            this._debugFtpCommand = false;
            this._enableCertificateValidation = true;
            this._serverTimeZone = "Europe/London";
            this._user = null;
            this._password = null;
            this._host = null;
            this._port = 21;
            this._timeout = 60000;
            this._socketTimeout = 60000;
            this._bufferSizeInBytes = 8192;
            this._trustStorePath = null;
            this._trustStorePassword = null;
            this._keyStorePath = null;
            this._keyStorePassword = null;
            this._keyPassword = null;
            this._keyAlias = null;
            this._keystoreType = null;
            this._trustStoreType = null;
            this._sslContextCache = true;

            INSTANCE = this;
        }

        public FTPSConnection connect(FTPSConnectionProvider provider) throws ConnectionException {
            FTPSConnection connect = provider.connect();
            return connect;
        }

        public Builder withTLSV12(boolean tls12) {
            this._tlsV12Only = tls12;
            return INSTANCE;
        }

        public Builder withImplicit(boolean isImplicit) {
            this._isImplicit = isImplicit;
            return INSTANCE;
        }

        public Builder withSSLContext(SSLContext sslContext) {
            if (Objects.isNull(sslContext)) {
                throw new NullPointerException("sslContext can not be null");
            }
            FTPClientProxyFactory._sslContext_ = sslContext;
            return INSTANCE;
        }

        public Builder withKeyStore(String keyStoreType) {
            if (Strings.isNullOrEmpty(keyStoreType)) {
                throw new NullPointerException("keyStoreType is null");
            }
            this._keystoreType = keyStoreType;
            return INSTANCE;
        }

        public Builder withKeyStore(String keystorePath,
                                    String keystorePassword,
                                    String keyPassword,
                                    String keyAlias) {
            return this.withKeyStore(keystorePath, keystorePassword, keyPassword, keyAlias, KeyStore.getDefaultType());
        }

        public Builder withKeyStore(String keystorePath,
                                    String keystorePassword,
                                    String keyPassword,
                                    String keyAlias,
                                    String keyStoreType) {

            if (Strings.isNullOrEmpty(keystorePath) ||
                    Strings.isNullOrEmpty(keystorePassword) ||
                    Strings.isNullOrEmpty(keyAlias) ||
                    Strings.isNullOrEmpty(keyStoreType) ||
                    Strings.isNullOrEmpty(keyPassword)) {
                throw new NullPointerException("keystorePath, keystorePassword keyAlias, keyStoreType are mandatory, none of them can be null");
            }
            this._keyStorePath = keystorePath;
            this._keyStorePassword = keystorePassword;
            this._keyAlias = keyAlias;
            this._keystoreType = keyStoreType;
            this._keyPassword = keyPassword;

            return INSTANCE;
        }

        public Builder withTrustStore(String truststorePath,
                                      String trustStorePassword) {
            return this.withTrustStore(truststorePath, trustStorePassword, KeyStore.getDefaultType());
        }

        public Builder withTrustStore(String truststorePath,
                                      String trustStorePassword,
                                      String trustStoreType) {
            if (Strings.isNullOrEmpty(truststorePath) ||
                    Strings.isNullOrEmpty(trustStorePassword) ||
                    Strings.isNullOrEmpty(trustStoreType)) {
                throw new NullPointerException("truststorePath, and trustStorePassword are mandatory, none of them can be null");
            }
            this._trustStorePath = truststorePath;
            this._trustStorePassword = trustStorePassword;
            this._trustStoreType = trustStoreType;
            return INSTANCE;
        }

        public Builder withSessionReuse(boolean reuse) {
            this._sessionReuse = reuse;
            return INSTANCE;
        }

        public Builder withpPintFtpCommand(boolean printFTPCommand) {
            this._debugFtpCommand = printFTPCommand;
            return INSTANCE;
        }

        public Builder withEnableCertificateValidation(boolean enableCertificateValidation) {
            this._enableCertificateValidation = enableCertificateValidation;
            return INSTANCE;
        }

        public Builder withServerTimeZone(String serverTimeZone) {
            this._serverTimeZone = serverTimeZone;
            return INSTANCE;
        }

        public Builder withUsername(String user) {
            this._user = user;
            return INSTANCE;
        }

        public Builder withPassword(String password) {
            this._password = password;
            return INSTANCE;
        }

        public Builder withHost(String host) {
            this._host = host;
            return INSTANCE;
        }

        public Builder withPort(int port) {
            this._port = port;
            return INSTANCE;
        }

        public Builder withTimeout(int timeout) {
            this._timeout = timeout;
            return INSTANCE;
        }

        public Builder withSocketTimeout(int socketTimeout) {
            this._socketTimeout = socketTimeout;
            return INSTANCE;
        }

        public Builder withBufferSizeInBytes(int bufferSizeInBytes) {
            this._bufferSizeInBytes = bufferSizeInBytes;
            return INSTANCE;
        }

        public Builder withSSLContextCache(boolean sslContextCache) {
            this._sslContextCache = sslContextCache;
            return INSTANCE;
        }

        public FTPSClientProxy build() throws ConnectionException {

            if (this._sslContextCache == false) {
                _sslContext_ = this.sslContext();
            } else if (_sslContext_ == null) {
                _lock_.lock();
                try {
                    if (_sslContext_ == null) {
                        _sslContext_ = this.sslContext();
                    }
                } finally {
                    _lock_.unlock();
                }
                cacheBuilder(this);
            }
            FTPSClientProxy proxy =  new FTPSClientProxy(
                    _isImplicit,
                    _sslContext_,
                    _sessionReuse,
                    _debugFtpCommand,
                    _enableCertificateValidation,
                    _serverTimeZone,
                    _user,
                    _password,
                    _host,
                    _port,
                    _timeout,
                    _socketTimeout,
                    _bufferSizeInBytes);
            proxy.connect();
            return proxy;

        }
        public boolean canIReuse() {
            return Objects.nonNull(_builder_);
        }

        private SSLContext sslContext() throws ConnectionException {

            SSLFactory.Builder builder = SSLFactory.builder();
            builder.withSecureRandom(new SecureRandom());
            if (this._tlsV12Only) {
                if (_logger.isDebugEnabled()) _logger.debug("**** TLS Protocols version: TLSv1.2");
                builder.withProtocols("TLSv1.2");
            }
            if (this._keyStorePath != null) {
                if (this._keyPassword == null || this._keyStorePassword == null || this._keyAlias == null) {
                    throw new IllegalArgumentException("Key Store location, its password, alias and key password must be provided");
                }
                if (_logger.isDebugEnabled()) _logger.debug("**** Reading keystore {}", this._keyStorePath);
                try (InputStream stream = FTPSUtil.getStream(this._keyStorePath)) {
                    if (Strings.isNullOrEmpty(this._keystoreType))
                        this._keyStorePath = KeyStore.getDefaultType();

                    KeyStore keyStore = KeyStore.getInstance(this._keystoreType);
                    keyStore.load(stream, this._keyStorePassword.toCharArray());
                    Map<String, char[]> map = new HashMap();
                    map.put(this._keyAlias, this._keyPassword.toCharArray());
                    X509ExtendedKeyManager keyManager = KeyManagerUtils.createKeyManager(keyStore, map);
                    builder.withIdentityMaterial(keyManager);
                } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                    if (_logger.isDebugEnabled()) _logger.debug("**** Unable to load keystore: {}", e.getMessage(), e);
                    throw new ConnectionException(e);
                }
            }
            if (this._enableCertificateValidation == true) {
                if (_logger.isDebugEnabled()) _logger.debug("**** With JDK and OS default trust Manager");
                builder.withDefaultTrustMaterial();
                builder.withSystemTrustMaterial();
            } else {
                if (_logger.isDebugEnabled())
                    _logger.debug("**** Unsafe Trust Manager. Insecure, not recommended for production use");
                builder.withUnsafeTrustMaterial();
                builder.withUnsafeHostnameVerifier();
            }

            if (this._trustStorePath != null) {
                if (this._trustStorePassword == null)
                    throw new IllegalArgumentException("Trust Store location and its password must be provided");

                if (Strings.isNullOrEmpty(this._trustStoreType))
                    this._trustStoreType = KeyStore.getDefaultType();

                if (_logger.isDebugEnabled()) _logger.debug("**** Reading TrustStore. {}", this._trustStorePath);
                builder.withTrustMaterial(FTPSUtil.getStream(this._trustStorePath), this._trustStorePassword.toCharArray(), this._trustStoreType);
            }

            return builder.build().getSslContext();
        }
    }
}
