package cc.clancollective.plugin.net;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Locks in the cancellation contract that {@link WebhookClient#cancelPendingRetries()} relies on:
 * retry deliveries are scheduled on an injected {@link ScheduledExecutorService} and tracked as
 * {@link ScheduledFuture}s so that, when the plugin shuts down, pending retries can be cancelled
 * before they fire. The executor itself is owned by RuneLite and is not shut down here.
 */
public class WebhookShutdownTest
{
	@Test
	public void cancellingTrackedFuturesPreventsThemRunning() throws Exception
	{
		final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try
		{
			final Set<ScheduledFuture<?>> pending = ConcurrentHashMap.newKeySet();
			final AtomicInteger ran = new AtomicInteger();

			// Schedule several "retries" far enough out that cancellation wins the race.
			for (int i = 0; i < 5; i++)
			{
				pending.add(executor.schedule(ran::incrementAndGet, 500, TimeUnit.MILLISECONDS));
			}

			// Mirror cancelPendingRetries(): cancel every tracked future, then clear.
			for (final ScheduledFuture<?> f : pending)
			{
				f.cancel(false);
			}
			pending.clear();

			// Give the executor well past the original delay; nothing should have run.
			Thread.sleep(800);
			assertEquals(0, ran.get());
			assertTrue(pending.isEmpty());
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	public void alreadyRunFuturesAreHarmlessToCancel() throws Exception
	{
		final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try
		{
			final ScheduledFuture<?> f = executor.schedule(() -> { }, 0, TimeUnit.MILLISECONDS);
			f.get(1, TimeUnit.SECONDS); // ensure it has completed
			// Cancelling a completed future is a no-op and must not throw.
			f.cancel(false);
		}
		finally
		{
			executor.shutdownNow();
		}
	}
}
