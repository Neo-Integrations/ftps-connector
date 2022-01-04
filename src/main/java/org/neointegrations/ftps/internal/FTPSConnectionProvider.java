package org.neointegrations.ftps.internal;

import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.util.KeyManagerUtils;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.api.connection.PoolingConnectionProvider;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.*;
import org.neointegrations.ftps.internal.client.FTPSClientProxy;
import org.neointegrations.ftps.internal.util.FTPSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static org.mule.runtime.api.meta.model.display.PathModel.Location.EXTERNAL;
import static org.mule.runtime.api.meta.model.display.PathModel.Type.DIRECTORY;


public class FTPSConnectionProvider implements PoolingConnectionProvider<FTPSConnection> {

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
    @DisplayName("Key Store Path")
    @Parameter
    private String keyStorePath;

    @Optional()
    @Placement(tab = "SSL Context", order = 6)
    @DisplayName("Key Store Password")
    @Password
    @Parameter
    private String keyStorePassword;

    @Optional()
    @Placement(tab = "SSL Context", order = 7)
    @DisplayName("Private Key Password")
    @Password
    @Parameter
    private String keyPassword;

    @Optional()
    @Placement(tab = "SSL Context", order = 8)
    @DisplayName("Key Alias")
    @Parameter
    private String keyAlias;

    private SSLContext _sslContext = null;

    public FTPSConnectionProvider() throws ConnectionException {
        super();
        // To resolve [NET-408 Issue](https://issues.apache.org/jira/browse/NET-408), below property is needed
        // to share SSL session with the data connection
        System.setProperty("jdk.tls.useExtendedMasterSecret", "false");


    }

    @Override
    public FTPSConnection connect() throws ConnectionException {
        if (_logger.isDebugEnabled()) _logger.debug("Connection starting...");
        if(_sslContext == null) {
            _lock.lock();
            try {
                if(_sslContext == null) _sslContext = this.sslContext();
            } finally {
                _lock.unlock();
            }
        }

        final FTPSClientProxy client = new FTPSClientProxy(
                false,
                _sslContext,
                sslSessionReuse,
                debugFtpCommand,
                enableCertificateValidation,
                serverTimeZone,
                user,
                password,
                host,
                port,
                timeout,
                socketTimeout,
                bufferSizeInBytes);

        return new FTPSConnection(this, client);
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

    private SSLContext sslContext() throws ConnectionException {
        SSLFactory.Builder builder = SSLFactory.builder();
        builder.withSecureRandom(new SecureRandom());

        if(this.tlsV12Only) {
            if (_logger.isDebugEnabled()) _logger.debug("**** TLS Protocols version: TLSv1.2");
            builder.withProtocols("TLSv1.2");
        }

        if(keyStorePath != null) {
            if(keyPassword == null || keyStorePassword == null || keyAlias == null) {
                throw new IllegalArgumentException("Key Store location, its password, alias and key password must be provided");
            }
            if (_logger.isDebugEnabled()) _logger.debug("**** Reading keystore {}", keyStorePath);
            try(InputStream stream = FTPSUtil.getStream(this.keyStorePath)) {
            //try (InputStream stream = Files.newInputStream(Paths.get(this.keyStorePath))) {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(stream, keyStorePassword.toCharArray());
                Map<String, char[]> map = new HashMap();
                map.put(keyAlias, keyPassword.toCharArray());
                X509ExtendedKeyManager keyManager = KeyManagerUtils.createKeyManager(keyStore, map);
                builder.withIdentityMaterial(keyManager);
            } catch (KeyStoreException e) {
                if (_logger.isDebugEnabled()) _logger.debug("**** Invalid key store: {}", e.getMessage(), e);
                throw new ConnectionException(e);
            } catch (IOException e) {
                if (_logger.isDebugEnabled()) _logger.debug("**** Unable to read keystore: {}", e.getMessage(), e);
                throw new ConnectionException(e);
            } catch (CertificateException e) {
                if (_logger.isDebugEnabled()) _logger.debug("**** Certificate issue: {}", e.getMessage(), e);
                throw new ConnectionException(e);
            } catch (NoSuchAlgorithmException e) {
                if (_logger.isDebugEnabled()) _logger.debug("**** Invalid Algorithm: {}", e.getMessage(), e);
                throw new ConnectionException(e);
            }
        }
        if(this.enableCertificateValidation == true) {
            if (_logger.isDebugEnabled()) _logger.debug("**** With JDK and OS default trust Manager");
            builder.withDefaultTrustMaterial();
            builder.withSystemTrustMaterial();
        } else {
            if (_logger.isDebugEnabled()) _logger.debug("**** Unsafe Trust Manager. Insecure, not recommended for production use");
            builder.withUnsafeTrustMaterial();
            builder.withUnsafeHostnameVerifier();
        }
        if(this.trustStorePath != null) {
            if(trustStorePassword == null)
                throw new IllegalArgumentException("Trust Store location and its password must be provided");

            if (_logger.isDebugEnabled()) _logger.debug("**** Reading TrustStore. {}", trustStorePath);
            builder.withTrustMaterial(FTPSUtil.getStream(this.trustStorePath), this.trustStorePassword.toCharArray());
        }

        return builder.build().getSslContext();
    }

}
