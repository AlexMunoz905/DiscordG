package io.github.hlaaftana.discordg.request

import java.util.concurrent.*
import java.util.zip.Inflater

import groovy.json.JsonOutput

import org.eclipse.jetty.websocket.api.*
import org.eclipse.jetty.websocket.api.annotations.*
import org.eclipse.jetty.websocket.api.extensions.Frame

import java.util.Map

import io.github.hlaaftana.discordg.util.*
import io.github.hlaaftana.discordg.objects.*
import io.github.hlaaftana.discordg.objects.Server.VoiceState

/**
 * The websocket client for the API.
 * @author Hlaaftana
 */
@WebSocket
class WSClient{
	CountDownLatch latch = new CountDownLatch(1)
	Client client
	Session session
	Thread keepAliveThread
	def threadPool
	int responseAmount
	int heartbeats
	WSClient(Client client){ this.client = client; threadPool = Executors.newFixedThreadPool(client.eventThreadCount) }

	@OnWebSocketConnect
	void onConnect(Session session){
		Log.info "Connected to server."
		this.session = session
		this.session.policy.maxTextMessageSize = Integer.MAX_VALUE
		this.session.policy.maxTextMessageBufferSize = Integer.MAX_VALUE
		this.session.policy.maxBinaryMessageSize = Integer.MAX_VALUE
		this.session.policy.maxBinaryMessageBufferSize = Integer.MAX_VALUE
		identify()
		Log.info "Sent API details."
		latch.countDown()
	}

	@OnWebSocketMessage
	void onMessage(Session session, String message){
		if (client.ignoreTyping && message.contains("\"t\":\"TYPING_START\"")) return
		if (client.ignorePresenceUpdate && message.contains("\"t\":\"PRESENCE_UPDATE\"")) return
		if (!client.loaded && !message.contains("\"t\":\"READY\"")) return
		threadPool.submit({
			Map content = JSONUtil.parse(message)
			int op = content["op"]
			if (op == 0){
				String type = content["t"]
				Map data = content["d"]
				this.responseAmount = content["s"]
				if (type.equals("READY")){
					long heartbeat = data["heartbeat_interval"]
					try{
						keepAliveThread = new Thread({
							while (true){
								this.send(["op": 1, "d": System.currentTimeMillis().toString()])
								try{ Thread.sleep(heartbeat) }catch (InterruptedException ex){}
							}
						})
						keepAliveThread.setDaemon(true)
						keepAliveThread.start()
					}catch (e){
						e.printStackTrace()
						System.exit(0)
					}
					client.readyData << data
					client.readyData.guilds.each { Map g ->
						g.members.each { Map m ->
							m["guild_id"] = g["id"]
						}
						g.channels.each { Map c ->
							c["guild_id"] = g["id"]
							if (c["type"] == "text"){
								c["cached_messages"] = []
							}
						}
						g.emojis.each { Map e ->
							e["guild_id"] = g["id"]
						}
						g.roles.each { Map e ->
							e["guild_id"] = g["id"]
						}
					}
					client.readyData.private_channels.each { Map pc ->
						pc["cached_messages"] = []
					}
					Log.info "Done loading."
				}
				if (!client.loaded) return
				Map eventData = [:]
				Closure t = { String ty -> return ty.equals(type) }
				// i removed the switch here because it was slow
				try{
					if (t("READY") || t("USER_GUILD_SETTINGS_UPDATE") || t("USER_SETTINGS_UPDATE") || t("MESSAGE_ACK")){
						eventData = data
					}else if (t("HEARTBEAT_ACK")){
						heartbeats++
					}else if (t("CHANNEL_CREATE") || t("CHANNEL_DELETE") || t("CHANNEL_UPDATE")){
						if (!data.containsKey("guild_id")){
							eventData = [
								server: null,
								guild: null,
								channel: new PrivateChannel(client, data)
								]
						}else if (data["type"].equals("text")){
							eventData = [
								server: client.getServerById(data["guild_id"]),
								guild: client.getServerById(data["guild_id"]),
								channel: new TextChannel(client, data)
								]
						}else if (data["type"].equals("voice")){
							eventData = [
								server: client.getServerById(data["guild_id"]),
								guild: client.getServerById(data["guild_id"]),
								channel: new VoiceChannel(client, data)
								]
						}
					}else if (t("GUILD_BAN_ADD") || t("GUILD_BAN_REMOVE")){
						eventData = [
							server: client.getServerById(data["guild_id"]),
							guild: client.getServerById(data["guild_id"]),
							user: new User(client, data["user"])
							]
					}else if (t("GUILD_CREATE")){
						eventData = [
							server: new Server(client, data),
							guild: new Server(client, data)
							]
					}else if (t("GUILD_DELETE")){
						eventData = [
							server: client.getServerById(data["id"]),
							guild: client.getServerById(data["id"])
							]
					}else if (t("GUILD_INTEGRATIONS_UPDATE")){
						eventData = [
							server: client.getServerById(data["guild_id"]),
							guild: client.getServerById(data["guild_id"])
							]
					}else if (t("GUILD_EMOJIS_UPDATE")){
						eventData = [
							server: client.getServerById(data["guild_id"]),
							guild: client.getServerById(data["guild_id"]),
							emojis: data["emojis"].collect { new Emoji(client, it + ["guild_id": data["guild_id"]]) }
							]
					}else if (t("GUILD_MEMBER_ADD") || t("GUILD_MEMBER_UPDATE")){
						eventData = [
							server: client.getServerById(data["guild_id"]),
							guild: client.getServerById(data["guild_id"]),
							member: new Member(client, data)
							]
					}else if (t("GUILD_MEMBER_REMOVE")){
						eventData = [
							server: client.getServerById(data["guild_id"]),
							guild: client.getServerById(data["guild_id"]),
							member: { -> try{
								return client.getServerById(data["guild_id"]).members.find { it.id == data["user"]["id"] }
							}catch (ex){
								return null
							} }()
						]
					}else if (t("GUILD_ROLE_CREATE") || t("GUILD_ROLE_UPDATE")){
						eventData = [
							server: client.getServerById(data["guild_id"]),
							guild: client.getServerById(data["guild_id"]),
							role: new Role(client, data["role"])
							]
					}else if (t("GUILD_ROLE_DELETE")){
						eventData = [
							server: client.getServerById(data["guild_id"]),
							guild: client.getServerById(data["guild_id"]),
							role: client.getServerById(data["guild_id"]).getRoles().find { it.getId().equals(data["role_id"]) }
							]
					}else if (t("GUILD_UPDATE")){
						eventData = [
							server: new Server(client, data),
							guild: new Server(client, data)
							]
					}else if (t("MESSAGE_CREATE")){
						try{
							Message messageO = new Message(client, data)
							eventData = [
								message: messageO,
								sendMessage: messageO.channel.&sendMessage,
								sendFile: messageO.channel.&sendFile,
								author: messageO.channel?.server?.members?.find { try{ it?.id == messageO.author.id }catch (ex){ false } } ?: messageO.author
								]
						}catch (ex){
							println client.servers
							println client.servers*.textChannels.sum().find { it.id == data["channel_id"] }
						}
					}else if (t("MESSAGE_DELETE")){
						List<TextChannel> channels = client.textChannels
						eventData = [
							channel: channels.find { it.id == data["channel_id"] },
							message: channels*.cachedLogs.find { it.id == data["id"] } ?: data["id"]
							]
					}else if (t("MESSAGE_UPDATE")){
						if (data.containsKey("content")){
							eventData = [
								message: new Message(client, data)
								]
						}else{
							List<TextChannel> channels = []
							client.servers.each { channels << it.textChannels }
							channels << client.privateChannels
							eventData = [
								channel: client.getTextChannelById(data["channel_id"]),
								message: channels*.cachedLogs.find { it.id == data["id"] } ?: data["id"],
								embeds: data["embeds"]
								]
						}
					}else if (t("PRESENCE_UPDATE")){
						eventData = [
							server: client.servers.find { it.id == data["guild_id"] },
							guild: client.servers.find { it.id == data["guild_id"] },
							member: { ->
								try{
									return client.members.find { it.id == data["user"]["id"] && it.server.id == client.servers.find { it.id == data["guild_id"] }.id } ?: client.members.find { it.id == data["user"]["id"] }
								}catch (ex){
									return null
								}
							}(),
							game: (data["game"] != null) ? data["game"]["name"] : "",
							status: data["status"]
						]
						// this here is sort of dangerous
						if (data["user"]["username"] != null){ eventData["newUser"] = new User(client, data["user"]) }
					}else if (t("TYPING_START")){
						eventData = [
							channel: client.getTextChannelById(data["channel_id"]),
							user: client.getTextChannelById(data["channel_id"]).server.members.find { it.id == data["user_id"] }
							]
					}else if (t("VOICE_STATE_UPDATE")){
						eventData = [
							voiceState: new VoiceState(client, data)
							]
						if (data["user_id"] == client.user.id){
							if (client.voiceWsClient != null){ // voice connected
								client.voiceData.channel = client.getVoiceChannelById(data["channel_id"])
							}
							client.voiceData.session_id = data["session_id"]
						}
					}else if (t("VOICE_SERVER_UPDATE")){
						client.voiceData << data
						eventData = data
					}else if (t("USER_UPDATE")){
						eventData = [
							user: new User(client, data)
						]
					}else{
						eventData = data
						String fileName = "dumps/${type}_${System.currentTimeMillis()}.json"
						Log.info "Unhandled websocket message: $type. Report to me, preferrably with the data in $fileName."
						File dump = new File(fileName)
						new File("dumps").mkdirs()
						dump.createNewFile()
						dump.write(JsonOutput.prettyPrint(JsonOutput.toJson(data)))
					}
				}catch (ex){
					if (Log.enableEventRegisteringCrashes) ex.printStackTrace()
					Log.info "Ignoring exception from event object registering"
				}
				if (!t("READY")){ eventData.put("fullData", data)
				}else if (client.copyReady){ eventData.put("fullData", data) }
				Map event = eventData
				if (client.isLoaded()){
					Thread.start { client.dispatchEvent(type, event) }
					Thread.start { client.dispatchEvent("ALL", event) }
				}
			}else if (op == 9){
				responseAmount = 0
				heartbeats = 0
				identify()
			}
		} as Callable)
	}

	@OnWebSocketClose
	void onClose(Session session, int code, String reason){
		Log.info "Connection closed. Reason: $reason, code: $code"
		if (keepAliveThread != null) keepAliveThread.interrupt(); keepAliveThread = null
	}

	@OnWebSocketError
	void onError(Throwable t){
		t.printStackTrace()
	}

	/**
	 * Converts an object to a string then sends it as a websocket message. Converts Maps to JSON strings.
	 * @param message - the object to convert to a string.
	 */
	void send(Object message){
		try{
			if (message instanceof Map)
				session.remote.sendString(JSONUtil.json(message))
			else
				session.remote.sendString(message.toString())
		}catch (e){
			e.printStackTrace()
		}
	}

	void identify(){
		Map a = [
			"op": 2,
			"d": [
				"token": client.token,
				"v": 4,
				"large_threshold": client.largeThreshold,
				"properties": [
					"\$os": System.getProperty("os.name"),
					"\$browser": "DiscordG",
					"\$device": "Groovy",
					"\$referrer": "https://discordapp.com/@me",
					"\$referring_domain": "discordapp.com",
				],
			],
		]
		this.send(a)
	}
}


