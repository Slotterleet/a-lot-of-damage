package alod;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.scene.style.TextureRegionDrawable;
import arc.struct.ObjectFloatMap;
import arc.util.*;
import mindustry.content.StatusEffects;
import mindustry.core.GameState;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.Fonts;

import static mindustry.Vars.world;
import static mindustry.game.EventType.*;

/**
 * A class for displaying damage values entities receive.
 * Taken straight from FOS, repurposed for this mod.
 * @author Slotterleet
 */
public class DamageDisplay {
    /** Stores previous building health values. */
    public static ObjectFloatMap<Building> buildings;
    /** Stores previous unit health values. */
    public static ObjectFloatMap<Unit> units;

    /**
     * Stol- I mean kindly provided by @nekit508.
     */
    public static Effect damageShowEffect = new Effect(60f, 80f, (e) -> {
        DamageInfo data = e.data();
        var bullet = data.bullet;
        float damage = data.damage;

        float dmgScale = Mathf.clamp(1f + damage / 2500, 1f, 5f);
        float scale = Mathf.sin(e.fout() * 3.14f / 2f) * dmgScale;

        if (scale == 0) return;

        Color color =
            damage == 0 ? Color.gray :
                data.type == 2 ? Pal.heal :
                    data.target.color;
        TextureRegionDrawable forDraw = null;
        switch (data.type) {
            case 1 -> forDraw = Icon.modeAttackSmall;
            case 2 -> forDraw = Icon.wrench;
        }

        Draw.z(Layer.effect + 20);
        Draw.color(color.cpy().a(0.8f));

        float realScale = scale * 0.5f;

        if (forDraw != null) {
            TextureRegion r = forDraw.getRegion();
            Draw.rect(r, e.x, e.y, r.width * realScale / 2f, r.height * realScale / 2f);
        }
        Fonts.def.draw(Strings.fixed(damage, damage < 50 ? 1 : 0), e.x + (forDraw == null ? 0f : forDraw.getRegion().width * realScale / 4f),
            e.y + (forDraw == null ? 0f : forDraw.getRegion().height * realScale / 4f), Draw.getColor(), realScale, false, Align.left);

        // use a string containing word "crit" as bullet data for that satisfying "CRITICAL HIT!!!" text.
        if (bullet != null && bullet.data instanceof String s && s.contains("crit")) {
            var critColor = new Color(e.fin(), e.fout(), 0, e.fout());
            Fonts.def.draw("CRITICAL", e.x, e.y - 16f,
                critColor, 1f, false, Align.center);
            Fonts.def.draw("HIT!!!", e.x, e.y - 32f,
                critColor, 1f, false, Align.center);
        }

        Draw.z(Layer.effect);
        Draw.color();
    });

    static {
        // ?????????????????????????????????
        Events.on(UnitDamageEvent.class, e -> {
            //if a unit is invincible, just show 0 damage, as simple as that.
            if (e.unit.hasEffect(StatusEffects.invincible) || e.unit.health == Float.POSITIVE_INFINITY) {
                showZero(e.unit);
                return;
            }

            float prev = units.get(e.unit, 0f);
            if (prev <= 0f) return;

            float dmg = prev - (e.unit.health + e.unit.shield);
            showDamage(dmg, e.bullet, e.unit);
            units.increment(e.unit, 0f, -(prev - (e.unit.health + e.unit.shield)));
            //remove a killed unit
            if (units.get(e.unit, 0f) <= 0f) {
                units.remove(e.unit, 0f);
            }
        });

        Events.on(StateChangeEvent.class, e -> {
            if (e.to != GameState.State.playing || e.from == GameState.State.paused) return;
            buildings = null;
            units = null;
        });

        Events.run(Trigger.update, () -> {
            //region buildings
            if (buildings == null) {
                initBuildings();
            } else {
                buildings.each(b -> {
                    //make sure to track heals
                    if (b.key.health > b.value) {
                        showHeal(b.key.health - b.value, b.key);
                        buildings.increment(b.key, 0f, b.key.health - b.value);
                    } else if (b.key.health < b.value) {
                        // TODO: somehow handle DoT better.
                        buildings.increment(b.key, 0f, b.key.health - b.value);
                    }
                });
            }
            //endregion

            //region units
            if (units == null) {
                initUnits();
            }
            Groups.unit.each(Unit::hittable, u -> units.put(u, u.health + u.shield));
            units.each(u -> {
                //make sure to track heals
                if (u.key.health > u.value) {
                    showHeal(u.key.health - u.value, u.key);
                    units.increment(u.key, 0f, u.key.health - u.value);
                } else if ((u.key.health + u.key.shield) < u.value) {
                    units.increment(u.key, 0f, (u.key.health + u.key.shield) - u.value);
                }
            });
            //endregion
        });

        //region buildings
        Events.on(BlockBuildEndEvent.class, e -> {
            if (buildings == null)
                initBuildings();
            Building b = world.build(e.tile.x, e.tile.y);
            if (b != null) {
                if (e.breaking) {
                    buildings.remove(b, 0f);
                } else {
                    buildings.put(b, b.health);
                    Log.debug("[ALOD] [green]Added[] building of type @, @ builds total", b.block.name, buildings.size);
                }
            }
        });

        Events.on(BuildDamageEvent.class, e -> {
            //if a building is invincible, just show 0 damage, as simple as that.
            if (e.build.health == Float.POSITIVE_INFINITY) {
                showZero(e.build);
                return;
            }

            float prev = buildings.get(e.build, 0f);
            if (prev <= 0f) return;

            float dmg = prev - e.build.health;
            showDamage(dmg, e.source, e.build);

            buildings.increment(e.build, 0f, -(prev - e.build.health));
            //remove a destroyed building
            if (buildings.get(e.build, 0f) <= 0f) {
                buildings.remove(e.build, 0f);
            }
        });
        //endregion
    }

    public DamageDisplay() {

    }

    private static void showZero(Teamc target) {
        float worldx = target.x() + Mathf.random(-6f, 6f);
        float worldy = target.y() + Mathf.random(16f, 24f);

        damageShowEffect.at(worldx, worldy, 0f, new DamageInfo(0, null, 0f, target.team()));
    }

    private static void showDamage(float damage, @Nullable Bullet bullet, Teamc target) {
        boolean pierce = bullet != null && bullet.type.pierceArmor;
        float scl = 1 + (Mathf.floor(damage / 200f) / 10f);
        float worldx = target.x() + Mathf.random(-12f, 12f) * scl;
        float worldy = target.y() + Mathf.random(16f, 24f) * scl;

        damageShowEffect.at(worldx, worldy, 0f, new DamageInfo(pierce ? 1 : 0, bullet, damage, target.team()));
    }

    private static void showHeal(float amount, Teamc target) {
        float worldx = target.x() + Mathf.random(-6f, 6f);
        float worldy = target.y() + Mathf.random(16f, 24f);

        damageShowEffect.at(worldx, worldy, 0f, new DamageInfo(2, null, amount, target.team()));
    }

    private static void initBuildings() {
        Time.mark();

        buildings = new ObjectFloatMap<>();
        for (int x = 0; x < world.width(); x++) {
            for (int y = 0; y < world.height(); y++) {
                Building b = world.build(x, y);
                if (b != null) {
                    buildings.put(b, b.health);
                }
            }
        }

        Log.debug("[ALOD] Initiated @ buildings in @ms", buildings.size, Time.elapsed());
    }

    private static void initUnits() {
        Time.mark();

        units = new ObjectFloatMap<>();
    }

    static class DamageInfo {
        int type;
        @Nullable Bullet bullet;
        float damage;
        Team target;

        public DamageInfo(int t, Bullet b, float d, Team target) {
            type = t;
            bullet = b;
            damage = d;
            this.target = target;
        }
    }
}