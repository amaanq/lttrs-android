package rs.ltt.android.util;

import org.junit.Assert;
import org.junit.Test;

public class ConsistentColorGenerationTest {

    @Test
    public void samIsKeppel() {
        Assert.assertEquals(0xff389999, ConsistentColorGeneration.rgb("sam@example.com"));
    }

    @Test
    public void ashIsLochinvar() {
        Assert.assertEquals(0xff379a8f, ConsistentColorGeneration.rgb("ash@example.com"));
    }

    @Test
    public void frankieIsBostonBlue() {
        Assert.assertEquals(0xff3998A1, ConsistentColorGeneration.rgb("frankie@example.com"));
    }

    @Test
    public void harperIsPortage() {
        Assert.assertEquals(0xffa173ed, ConsistentColorGeneration.rgb("harper@example.com"));
    }

    @Test
    public void jordanIsCopper() {
        Assert.assertEquals(0xffcb7634, ConsistentColorGeneration.rgb("jordan@example.com"));
    }

    @Test
    public void kaneIsLuxorGold() {
        Assert.assertEquals(0xffab8634, ConsistentColorGeneration.rgb("kane@example.com"));
    }

    @Test
    public void maxIsSeaGreen() {
        Assert.assertEquals(0xff349e5b, ConsistentColorGeneration.rgb("max@example.com"));
    }

    @Test
    public void quinIsChateauGreen() {
        Assert.assertEquals(0xff359e5c, ConsistentColorGeneration.rgb("quin@example.com"));
    }

    @Test
    public void rileyIsMediumPurple() {
        Assert.assertEquals(0xffb16bed, ConsistentColorGeneration.rgb("riley@example.com"));
    }

    @Test
    public void xuIsBrilliantRose() {
        Assert.assertEquals(0xfff044b4, ConsistentColorGeneration.rgb("xu@example.com"));
    }
}
