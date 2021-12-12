# Mule FTPS Connector

### What is FTP?
> [From wikipedia](https://en.wikipedia.org/wiki/File_Transfer_Protocol) The File Transfer Protocol (FTP) is a standard communication protocol used for the transfer of computer files from a server to a client on a computer network. FTP is built on a client–server model architecture using separate control and data connections between the client and the server.[1] FTP users may authenticate themselves with a clear-text sign-in protocol, normally in the form of a username and password, but can connect anonymously if the server is configured to allow it. For secure transmission that protects the username and password, and encrypts the content, FTP is often secured with SSL/TLS (FTPS) or replaced with SSH File Transfer Protocol (SFTP)

### What is FTPS
> [From wikipedia](https://en.wikipedia.org/wiki/FTPS): FTPS (also known FTP-SSL, and FTP Secure) is an extension to the commonly used File Transfer Protocol (FTP) that adds support for the Transport Layer Security (TLS) and, formerly, the Secure Sockets Layer (SSL, which is now prohibited by RFC7568) cryptographic protocols.
> FTPS should not be confused with the SSH File Transfer Protocol (SFTP), a secure file transfer subsystem for the Secure Shell (SSH) protocol with which it is not compatible. It is also different from FTP over SSH, which is the practice of tunneling FTP through an SSH connection.

### FTPS vs SFTP
> The key distinguishing feature of `SFTP` and `FTPS` protocols is the underlying transport mechanism. While FTPS affixes an additional layer to the legacy FTP protocol, SFTP essentially acts as an extension to the `SSH` protocol. This means that both transport protocols do not share any association but exist to initiate a transfer of files between systems.

>> <b>FTPS:</b> `data channel` and `command channel` are used as two separate channels for facilitating exchanges on the FTPS protocol.
>> The command channel has the role of managing simple command exchanges between server and FTP client by usually running on server 21 port.
>> Accordingly, the data channel works by employing on-demand temporary ports that are listening on the client (active mode) or the server (passive mode). This channel holds the responsibility of data exchange in terms of file transfers or directory listings.

>> <b>SFTP:</b> SFTP does not make use of distinct data and command channels. Transfer within SFTP takes place through the means of a single connection through uniquely formatted packets.

### FTPS
MuleSoft is a java based application, so we will require Java `FTP/FTPS` client library to connect to a file share using `FTP` protocol. Based on various research, I have found that [Apache common Net's FTPS Client](https://commons.apache.org/proper/commons-net/) is the most upto date and actively maintained by Apache. There are many other commercial library which you can choose from but since I am making a community connector, I am sticking with the Apache library.

### Available connectors to choose from
Based on my knowledge there is only one [Mule FTPS Connector](https://docs.mulesoft.com/ftps-connector/1.6/) created and supported by MuleSoft. But the connector is a premium one and part of the B2B package. It means, anyone wants to use the MuleSoft's FTPS connector will require to buy its license separately.

[Mule FTPS Connector](https://docs.mulesoft.com/ftps-connector/1.6/) connector is highly recommanded if the organisation can sepend little extra as it provide complete support of the protocol and production tested in many organisations.

### Why create this connector?
The main reason to create this connector is to provide a community alternative option to the MuleSoft's paid connector. I will try my best to keep it upto date with any fixes and upgrade when Apache common net upgrade.


### User Guide

<b>Build your own</b>
> 1. Clone the [repo](git@github.com:Neo-Integrations/ftps-connector.git)
> 2. Change to the project directory - `cd ftps-connector`
> 3. To install the connector to the local maven repo, run `mvn clean install`.
> 4. Then include the below dependency in your Mule project to start using the connector:
> ```
>  <dependency>
>    <groupId>org.neointegrations</groupId>
>    <artifactId>ftps-connector</artifactId>
>    <version>1.0.0</version>
>    <classifier>mule-plugin</classifier>
>  <dependency>
>    ```
> 5. If you would like to deploy the connector to a maven repo, please include the distribution management in the pom.xml and publish to the maven artifact.

<b>Use the binary directly from maven repo</b>
> 1. First add following maven repository in your pom.xml's repository section
> ```xml
> <repositories>
> ...
> <repository>
>  <id>maven-public</id>
>  <url>https://pkgs.dev.azure.com/NeoIntegration/MuleSoft/_packaging/maven-public/maven/v1</url>
>  <releases>
>     <enabled>true</enabled>
>  </releases>
>  <snapshots>
>     <enabled>true</enabled>
>  </snapshots>
> </repository>
> ...
> </repositories>
> ```
> 2. Add following server details in your `$M2_HOME/settings.xml`. Replace `[PERSONAL_ACCESS_TOKEN]` with the actual password. Please [contact](mailto:aminul1983@gmail.com) me if you would like to get a token.
> ```xml
>   <servers>
>   ...
>    <server>
>      <id>maven-public</id>
>      <username>NeoIntegration</username>
>      <password>[PERSONAL_ACCESS_TOKEN]</password>
>    </server>
>    ...
>  </servers>
> ```
> 3. Thats it, you can start using it now.

### How to use the operations
#### Config
```xml

<ftps:config name="Ftps_Config" doc:name="Ftps Config"
    doc:id="75bcfcf1-20dc-485a-bd1a-9ebe5780d72d">
    <ftps:connection user="${USER_NAME}" password="${PASSWORD}"
        host="163.172.147.233" port="23" timeout="60000"
        socketTimeout="120000" bufferSizeInBytes="#[1024*1024]" remoteVerificationEnable="false"/>
</ftps:config>

```

#### As a listener
This is the most standard operation, use the FTPS connector to scan a folder for any new files or update to an existing file.

![Listener flow](./images/listener.png)

```xml	
<flow name="listener-flow" doc:id="22be816a-b834-4a3a-a677-e667a90d2a56" >
		<ftps:ftps-listener doc:name="On New or Updated File" doc:id="70b843fa-c14a-4dd0-b5f7-dab7052f5181" 
		config-ref="Ftps_Config" sourceFolder="/INBOUND" autoDelete="true" applyPostActionWhenFailed="false">
			<scheduling-strategy >
				<fixed-frequency frequency="300" timeUnit="SECONDS"/>
			</scheduling-strategy>
			<ftps:predicate-builder filenamePattern="*"/>
		</ftps:ftps-listener>
		<file:write doc:name="Write" doc:id="f7189dec-aeee-49cd-9d4c-4b179e97cd04" path="#['tmp/' ++ attributes.name]"/>
</flow>

```

#### To list files


![list](./images/list.png)
```xml
<flow name="list-flow" doc:id="7c085990-520f-46bb-be45-03123f76cbdb" >
    <http:listener doc:name="Listener" doc:id="6ece8999-74de-469b-a0b1-794442ce9b97" config-ref="HTTP_Listener_config" path="/list"/>
    <ftps:list doc:name="List File" doc:id="31686d10-e220-46de-825e-53028262cbe2" sourceFolder="/INBOUND" config-ref="Ftps_Config">
        <ftps:matcher filenamePattern="*" directories="EXCLUDE" symLinks="EXCLUDE"/>
    </ftps:list>
    <foreach doc:name="For Each" doc:id="ca7a3e6f-fc5f-4e90-9679-1d932fac7e1f" collection="#[payload]">
        <file:write doc:name="Write" doc:id="aee35f90-542d-4c21-a083-74b0733912be" path="#['tmp/' ++ attributes.name]"/>
    </foreach>
            <set-payload value="#[true]" doc:name="Set Payload" doc:id="50509215-a64f-42dc-9880-9ee1cee59a42" />
    <error-handler >
        <on-error-continue enableNotifications="true" logException="true" doc:name="On Error Continue" doc:id="c0928cdd-449c-4027-8bea-ec7b03ce7ac5" >
            <set-payload value="#[false]" doc:name="Set Payload" doc:id="cf9bda6a-552e-4589-8643-e49beddcb254" />
        </on-error-continue>
    </error-handler>
</flow>
```




### Advance options


### References
- [Apache Common Net](http://commons.apache.org/proper/commons-net/)
- [FTP Specification](https://datatracker.ietf.org/doc/html/rfc959)
- [FTPS Specification](https://datatracker.ietf.org/doc/html/rfc4217)


## Support
- The connector was created with the best effort basis. I would suggest anyone thinking of using this connector, to test it appropriately.
- You can raise any issue with the connector through issue tab, I will try to address them as quickly as I can.




