package cc.clancollective.plugin.util;

import lombok.Value;

@Value
public class Screenshot
{
	String filename;
	String mimeType;
	byte[] bytes;
}
