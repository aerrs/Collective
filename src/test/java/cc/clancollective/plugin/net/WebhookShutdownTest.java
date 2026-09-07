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

			for (int i = 0; i < 5; i++)
			{
				pending.add(executor.schedule(ran::incrementAndGet, 200, TimeUnit.MILLISECONDS));
			}

			for (final ScheduledFuture<?> f : pending)
			{
				assertTrue(f.cancel(false));
			}
			pending.clear();

			final ScheduledFuture<?> barrier = executor.schedule(() -> { }, 200, TimeUnit.MILLISECONDS);
			barrier.get(2, TimeUnit.SECONDS);

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
			f.get(1, TimeUnit.SECONDS);
			f.cancel(false);
		}
		finally
		{
			executor.shutdownNow();
		}
	}
}
