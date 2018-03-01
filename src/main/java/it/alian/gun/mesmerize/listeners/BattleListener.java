package it.alian.gun.mesmerize.listeners;

import it.alian.gun.mesmerize.MConfig;
import it.alian.gun.mesmerize.MTasks;
import it.alian.gun.mesmerize.Mesmerize;
import it.alian.gun.mesmerize.compat.*;
import it.alian.gun.mesmerize.compat.hook.MesmerizeHolograph;
import it.alian.gun.mesmerize.lore.LoreCalculator;
import it.alian.gun.mesmerize.lore.LoreInfo;
import it.alian.gun.mesmerize.lore.LoreParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.*;

public class BattleListener implements Listener {

    @EventHandler
    public void onAttackRange(PlayerInteractEvent event) {
        long nano = System.nanoTime();
        performRangeAttack(event);
        if (MConfig.debug)
            System.out.println(event.getEventName() + " processed in " + (System.nanoTime() - nano) * 1E-6 + " ms.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onShieldBlocking(EntityDamageByEntityEvent event) {
        if (event.isCancelled() && event.getEntity() instanceof Player && !event.getEntity().hasMetadata("NPC") &&
                ShieldBlocking.check((Player) event.getEntity()))
            event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        long nano = System.nanoTime();
        performAttack(event);
        if (MConfig.debug)
            System.out.println(event.getEventName() + " processed in " + (System.nanoTime() - nano) * 1E-6 + " ms.");
    }

    private static void performRangeAttack(PlayerInteractEvent event) {
        if (MConfig.Performance.enableLongerRange && event.getAction() == Action.LEFT_CLICK_AIR) {
            if (event.getPlayer() != null && !AttackSpeed.check(event.getPlayer()))
                return;
            if (event.getPlayer().getEquipment().getItemInHand() == null || event.getPlayer().getEquipment().getItemInHand().getType() == Material.AIR ||
                    !event.getPlayer().getEquipment().getItemInHand().hasItemMeta() || !event.getPlayer().getEquipment().getItemInHand().getItemMeta().hasLore())
                return;
            Map<Integer, Location> map = new HashMap<>();
            for (LivingEntity entity : event.getPlayer().getWorld().getLivingEntities()) {
                map.put(entity.getEntityId(), entity.getEyeLocation());
            }
            int playerId = event.getPlayer().getEntityId();
            World world = event.getPlayer().getWorld();
            Location source = event.getPlayer().getEyeLocation().clone();
            MTasks.execute(() -> {
                LoreInfo info = LoreParser.getByEntityId(playerId);
                List<Integer> list = new ArrayList<>();
                for (Map.Entry<Integer, Location> entry : map.entrySet()) {
                    Location target = entry.getValue();
                    if ((target.distance(source) < Math.min(MConfig.Performance.maxAttackRange, info.getAttackRange() + MConfig.General.baseAttackRange)) &&
                            (new Vector(target.getX() - source.getX(), target.getY() - source.getY(),
                                    target.getZ() - source.getZ()).angle(source.getDirection()) < (Math.PI / 48D))) {
                        list.add(entry.getKey());
                    }
                }
                list.sort(Comparator.comparing(integer -> map.get(integer).distanceSquared(source)));
                list.stream().findFirst().ifPresent(integer -> MTasks.runLater(() -> {
                    LivingEntity other = ((LivingEntity) Compat.getByEntityId(integer, world));
                    Player player = Objects.requireNonNull((Player) Compat.getByEntityId(playerId, world));
                    if (other != null && !other.hasMetadata("NPC") && player.hasLineOfSight(other) &&
                            !MesmerizeHolograph.isHolographEntity(other)) {
                        EntityDamageByEntityEvent e = new EntityDamageByEntityEvent(player,
                                Compat.getByEntityId(integer, world), EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                                AttackDamage.getAttackDamage(player.getEquipment().getItemInHand()));
                        Bukkit.getPluginManager().callEvent(e);
                        if (!e.isCancelled()) {
                            other.setLastDamageCause(e);
                            other.setLastDamage(e.getDamage());
                            other.setHealth(it.alian.gun.mesmerize.util.Math.constraint(other.getMaxHealth(), 0,
                                    other.getHealth() - e.getDamage()));
                            Vector vector = player.getLocation().getDirection().normalize().multiply(0.3).setY(0.2);
                            other.setVelocity(vector);
                        }
                    }
                }));
            });
        }
    }

    private static void performAttack(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            if (event.getEntity().hasMetadata("NPC"))
                return;
            LivingEntity entity = (LivingEntity) event.getEntity();
            LivingEntity source = null;
            boolean bow = false;
            if (event.getDamager() instanceof LivingEntity)
                source = ((LivingEntity) event.getDamager());
            if (event.getDamager() instanceof Projectile
                    && ((Projectile) event.getDamager()).getShooter() instanceof LivingEntity) {
                source = (LivingEntity) ((Projectile) event.getDamager()).getShooter();
                bow = true;
            }
            if (source == null)
                return;
            if (source instanceof Player && !AttackSpeed.check((Player) source))
                return;
            if (MesmerizeHolograph.isHolographEntity(entity))
                return;
            // 攻击
            LoreInfo[] info = new LoreInfo[]{LoreParser.getByEntityId(source.getEntityId()),
                    LoreParser.getByEntityId(entity.getEntityId())};
            // 攻击范围
            if ((!bow) && (info[0].getAttackRange() + MConfig.General.baseAttackRange) * (info[0].getAttackRange() + MConfig.General.baseAttackRange)
                    < source.getLocation().distanceSquared(entity.getLocation())) {
                event.setCancelled(true);
                return;
            }
            // 命中及闪避
            if (Math.random() > (MConfig.General.baseAccuracy + info[0].getAccuracy() - MConfig.General.baseDodge - info[1].getDodge())) {
                event.setCancelled(true);
                if (MConfig.CombatMessage.showOnMiss) {
                    LivingEntity finalSource = source;
                    MTasks.execute(() -> finalSource.sendMessage(String.format(MConfig.CombatMessage.onMiss, EntityName.get(entity))));
                }
                if (MConfig.CombatMessage.showOnDodge) {
                    LivingEntity finalSource = source;
                    MTasks.execute(() -> entity.sendMessage(String.format(MConfig.CombatMessage.onDodge, EntityName.get(finalSource))));
                }
                return;
            }
            // 会心一击
            // 设置伤害
            if (Math.random() < info[0].getSuddenDeath()) {
                event.setDamage(entity.getHealth());
                if (MConfig.CombatMessage.showOnSuddenDeath) {
                    LivingEntity finalSource = source;
                    MTasks.execute(() -> finalSource.sendMessage(String.format(MConfig.CombatMessage.onSuddenDeath, EntityName.get(entity)
                            , event.getDamage())));
                }
                return;
            } else {
                event.setDamage(LoreCalculator.finalDamage(event.getDamage(), info[0], info[1], source, entity, bow));
            }
            if (MConfig.CombatMessage.showOnDamage) {
                LivingEntity finalSource = source;
                MTasks.execute(() -> finalSource.sendMessage(String.format(MConfig.CombatMessage.onDamage, EntityName.get(entity)
                        , event.getDamage())));
            }
            // 反弹
            {
                double health = source.getHealth(), prev = health;
                health = health - LoreCalculator.finalReflect(event.getDamage(), info[1]);
                if (health < 0) health = 0;
                source.setHealth(health);
                if (MConfig.CombatMessage.showOnReflect && (prev - health) > 1E-6) {
                    LivingEntity finalSource = source;
                    double finalHealth = health;
                    MTasks.execute(() -> entity.sendMessage(String.format(MConfig.CombatMessage.onReflect, prev - finalHealth,
                            EntityName.get(finalSource))));
                }
            }
            // 吸血
            {
                double health = source.getHealth(), prev = health;
                health += info[0].getLifeSteal() * event.getDamage();
                if (health > source.getMaxHealth())
                    health = source.getMaxHealth();
                source.setHealth(health);
                if (MConfig.CombatMessage.showOnLifeSteal && (health - prev) > 1E-6) {
                    LivingEntity finalSource = source;
                    double finalHealth = health;
                    MTasks.execute(() -> finalSource.sendMessage(String.format(MConfig.CombatMessage.onLifeSteal, EntityName.get(entity),
                            finalHealth - prev)));
                }
            }
            // 刷新攻击速度
            if (source instanceof Player)
                AttackSpeed.update((Player) source);
        }
    }

    public static void init() {
        Bukkit.getPluginManager().registerEvents(new BattleListener(), Mesmerize.instance);
    }

}
