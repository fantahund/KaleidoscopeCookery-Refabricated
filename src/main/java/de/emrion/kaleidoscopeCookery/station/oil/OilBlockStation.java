package de.emrion.kaleidoscopeCookery.station.oil;

import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import net.kyori.adventure.key.Key;
import org.bukkit.Particle;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ölblock (OilBlock): Dekoblock aus 9 Öl. Im Mod rutschig und eine ewige Feuerquelle – beides geht mit einer
 * Barriere nicht, hier nur Optik und die Wachs-Partikel.
 */
public class OilBlockStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "oil_block");

    public OilBlockStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("oil_block", ITEM, true, 1f, 1f, false, OilBlockStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
    }

    @Override
    protected void save(PersistentDataContainer state) {
    }

    @Override
    public boolean isTicking() {
        return true;
    }

    @Override
    public void tick(long serverTick) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(50) == 0) {
            world().spawnParticle(Particle.WAX_OFF, position().x() + 0.5 + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1),
                    position().y() + 1 + random.nextDouble() / 3, position().z() + 0.5 + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1),
                    1, 0, 0, 0, 0);
        }
    }

    @Override
    public void updateDisplay() {
        showPart("body", Key.key("kaleidoscope_cookery", "block/oil_block"), blockTransform(0));
    }
}
