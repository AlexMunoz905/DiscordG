package io.github.hlaaftana.discordg.status

import io.github.hlaaftana.discordg.objects.MapObject
import io.github.hlaaftana.discordg.util.ConversionUtil

class StatusPage extends MapObject {
	StatusPage(Map object){ super(object) }

	String getId(){ this.object["id"] }
	String getName(){ this.object["name"] }
	String getUrl(){ this.object["url"] }
	String getRawUpdateTime(){ return this.object["updated_at"] }
	Date getUpdateTime(){ return ConversionUtil.fromJsonDate(this.object["updated_at"]) }
}
