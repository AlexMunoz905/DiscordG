package ml.hlaaftana.discordg.objects

enum Permissions {
	ALL_FALSE(0),
	ALL_TRUE(0b00000011111100111111110000111111),
	CHANNEL_ALL_TRUE(0b00000011111100111111110000011001),
	GENERAL_ALL_TRUE(0b00000000000000000000000000111111),
	TEXT_ALL_TRUE(0b00000000000000111111110000000000),
	VOICE_ALL_TRUE(0b00000011111100000000000000000000)

	int value

	Permissions(int value){ this.value = value }

	Map getMap(){
		return [
			createInstantInvite: this[0],
			ban: this[1],
			kick: this[2],
			manageRoles: this[3],
			manageChannels: this[4],
			manageChannel: this[4],
			manageServer: this[5],
			readMessages: this[10],
			sendMessages: this[11],
			sendTts: this[12],
			manageMessages: this[13],
			deleteMessages: this[13],
			embedLinks: this[14],
			embed: this[14],
			uploadFiles: this[15],
			sendFiles: this[15],
			attachFiles: this[15],
			readMessageHistory: this[16],
			mentionEveryone: this[17],
			connect: this[20],
			speak: this[21],
			talk: this[21],
			mute: this[22],
			deafen: this[23],
			move: this[24],
			voiceActivation: this[25],
			]
	}

	int set(String permissionName, boolean truth){
		for (p in this.map){
		// ok i'm confused here so i'm just gonna do very worst-case stuff
			if (p.key.toLowerCase() == permissionName.toLowerCase()){
				for (int i = 0; i < 33; i++){
					if (this.map[i] == p.value){
						this[i] = truth
					}
				}
			}
		}
	}

	boolean getAt(int index){
		return (this.value >> index) & 1 as boolean
	}

	int putAt(int index, boolean truth){
		if (truth) this.value |= (1 << index)
		else this.value &= ~(1 << index)
		return this.value
	}

	def asType(Class target){
		switch (target){
			case int:
			case Integer:
				return this.value
			case long:
			case Long:
				return this.value as Long
		}
		throw new ClassCastException("Can't coerce ${this.class} to ${target}")
	}
}
