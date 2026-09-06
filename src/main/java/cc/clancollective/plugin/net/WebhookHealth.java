package cc.clancollective.plugin.net;

import java.time.Instant;
import lombok.Value;

@Value
public class WebhookHealth
{
	public enum State
	{

		UNKNOWN,

		OK,

		WARN,

		ERROR
	}

	State state;
	Instant lastSuccess;
	Instant lastFailure;
	String lastError;

	public static WebhookHealth unknown()
	{
		return new WebhookHealth(State.UNKNOWN, null, null, null);
	}

	public WebhookHealth withSuccess()
	{
		return new WebhookHealth(State.OK, Instant.now(), lastFailure, null);
	}

	public WebhookHealth withWarning(final String error)
	{
		return new WebhookHealth(State.WARN, lastSuccess, Instant.now(), error);
	}

	public WebhookHealth withError(final String error)
	{
		return new WebhookHealth(State.ERROR, lastSuccess, Instant.now(), error);
	}
}
