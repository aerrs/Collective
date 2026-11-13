package cc.clancollective.plugin.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CollectiveApiTest
{
	@Test
	public void imagesOnlyComeFromOwnStaticFolder()
	{
		assertEquals("https://clancollective.cc/static/img/metrics/vorkath.png",
			CollectiveApi.image("/static/img/metrics/vorkath.png").toString());
		assertNull(CollectiveApi.image(null));
		assertNull(CollectiveApi.image(""));
		assertNull(CollectiveApi.image("//evil.example/static/img/x.png"));
		assertNull(CollectiveApi.image("https://evil.example/static/img/x.png"));
		assertNull(CollectiveApi.image("/api/bingo/watch"));
		assertNull(CollectiveApi.image("/static/img/../../api/x"));
		assertEquals("https://clancollective.cc/img/128/logos/1-abc.webp.png",
			CollectiveApi.image("/img/128/logos/1-abc.webp.png").toString());
		assertNull(CollectiveApi.image("/img/128/logos/1-abc.webp"));
	}

	@Test
	public void pagesOnlyOpenOnOwnSite()
	{
		assertEquals("https://clancollective.cc/clans/x/bingo",
			CollectiveApi.page("https://clancollective.cc/clans/x/bingo").toString());
		assertNull(CollectiveApi.page("https://clancollective.cc.evil.example/clans/x/bingo"));
		assertNull(CollectiveApi.page("http://clancollective.cc/clans/x/bingo"));
		assertNull(CollectiveApi.page("javascript:alert(1)"));
		assertNull(CollectiveApi.page(null));
	}
}
