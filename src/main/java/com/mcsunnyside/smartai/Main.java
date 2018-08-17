package com.mcsunnyside.smartai;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Main extends JavaPlugin implements Listener {
	ArrayList<UUID> limitList = new ArrayList<>();
	ArrayList<EntityType> controlingEntity = new ArrayList<>();
	private final String name = Bukkit.getServer().getClass().getPackage().getName();
    private final String version = name.substring(name.lastIndexOf('.') + 1);
    private final DecimalFormat format = new DecimalFormat("##.##");
    private Object serverInstance;
    private Field tpsField;
    private long lastSynctaskTime;
    private long lastASynctaskTime;
    double tps;
    boolean disableAI = false;
    boolean useDisabledAITag;
    String tagName;
    boolean debug;
	@Override
	public void onEnable() {
		saveDefaultConfig();
		reloadConfig();
		limitList.clear();
		controlingEntity.clear();
		serverInstance = null;
		tpsField = null;
		useDisabledAITag = getConfig().getBoolean("useAIDisabledTag");
		tagName = getConfig().getString("tagName");
		debug = getConfig().getBoolean("debug");
		try {
            serverInstance = getNMSClass("MinecraftServer").getMethod("getServer").invoke(null);
            tpsField = serverInstance.getClass().getField("recentTps");
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
		Bukkit.getPluginManager().registerEvents(this, this);
		//Start scan all loaded chunks turtle
		List<World> worlds =  Bukkit.getWorlds();
		for (World world : worlds) {
			debug("Scanning world: "+world.getName());
			Chunk[] chunks = world.getLoadedChunks();
			for (Chunk chunk : chunks) {
				for (Entity entity : chunk.getEntities()) {
					if(entity.getCustomName()!=null && entity.getCustomName().equals(tagName) || (entity.getCustomName()!=null && entity.getCustomName().equals("Disabled AI"))) {
						toggleAI(((LivingEntity)entity), true);
						((LivingEntity)entity).setCustomName(null);
						((LivingEntity)entity).setCustomNameVisible(false);
					}
					if(entityTypeCheck(entity)) {
						limitList.add(entity.getUniqueId());
					}
				}
				
			}
		}
		new BukkitRunnable() {
			@Override
			public void run() {
				long time = System.currentTimeMillis();
				ArrayList<UUID> removeingList = new ArrayList<UUID>();
				tps = Double.parseDouble(getTPS(0));
				disableAI = false; //false = enableAI
				//Check TPS
				if(tps<getConfig().getDouble("lowestTPS")) {
					disableAI = true;
				}else {
					disableAI = false;
				}
				//Set mob AI
				for (UUID entityUUID : limitList) {
					Entity mob = Bukkit.getEntity(entityUUID);
					LivingEntity livingMob = null;
					if(mob instanceof LivingEntity) {
						livingMob = (LivingEntity)mob;
						getLogger().info(livingMob.getType().name());
					}else {
						//Something not livingEntity, Removed from list.
						debug("Missing can't control entity from list...");
						removeingList.add(entityUUID);
						debug("Entity UUID: "+entityUUID.toString());
					}
					if(disableAI) {
						try {
							toggleAI(livingMob, false);
						} catch (Exception e) {
							//Something not livingEntity, Removed from list.
							getLogger().warning("A mob can't improve the AI,Removeing from list...");
							getLogger().warning("This message not should always print on console,If you see this many time,Please check your config.");
							removeingList.add(entityUUID);
						}
					}else {
						try {
							toggleAI(livingMob, true);
						} catch (Exception e) {
							//Something not livingEntity, Removed from list.
							getLogger().warning("A mob can't improve the AI,Removeing from list...");
							getLogger().warning("This message not should always print on console,If you see this many time,Please check your config.");
							removeingList.add(entityUUID);
						}
					}
				}
				for (UUID remove : removeingList) {
					limitList.remove(remove);
				}
				removeingList.clear();
				lastSynctaskTime = System.currentTimeMillis()-time;
			}
		}.runTaskTimer(this, 600, 600);
		new BukkitRunnable() {
			@Override
			public void run() {
				//ASync task,Scan all in world mob, Because not all mobs spawn event can be listend.
				long time = System.currentTimeMillis();
				List<World> worlds =  Bukkit.getWorlds();
				for (World world : worlds) {
					Chunk[] chunks = world.getLoadedChunks();
					for (Chunk chunk : chunks) {
						for (Entity entity : chunk.getEntities()) {
							if(entityTypeCheck(entity)) {
								if(!limitList.contains(entity.getUniqueId())){
									limitList.add(entity.getUniqueId());
									debug("Added a mob in list from world: "+entity.getUniqueId().toString());
								}
							}
						}
						
					}
				}
				lastASynctaskTime = System.currentTimeMillis()-time;
			}
		}.runTaskTimerAsynchronously(this, 120, 120);
	}
	public void toggleAI(LivingEntity entity , boolean enable) {
		String entityCustomName = entity.getCustomName();
		if(enable) {
			if(entity.getCustomName()!=null && entity.getCustomName().equals(tagName) || entity.getCustomName()==null) {
				entity.setAI(true);
				if(useDisabledAITag) {
					entity.setCustomName(null);
					entity.setCustomNameVisible(false);
				}else if(entityCustomName!=null && entityCustomName.equals(tagName)) {
					entity.setCustomName(null);
					entity.setCustomNameVisible(false);	
				}
			}else {
				entity.setAI(false);
				if(entity.getCustomName()==null && useDisabledAITag) {
					entity.setCustomName(tagName);
					entity.setCustomNameVisible(true);
				}
			}
		}
	}
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(label.equals("smartai") && sender.hasPermission("smartai.admin")) {
			if(args.length==1) {
				switch (args[0].toString()) {
				case "reload":
					onDisable();
					onEnable();
					sender.sendMessage("Reloaded");
					return true;
				case "status":
					sender.sendMessage("¡ìaSmartAI status ¡ìb>> ¡ìdLast sync task usage time: ¡ìe"+lastSynctaskTime+"ms¡ìd, Last async task usage time: ¡ìe"+lastASynctaskTime+"ms¡ìd, Now have ¡ìe"+limitList.size()+" mobs ¡ìdin control list, Mobs AI status now: ¡ìr"+getAIMode()+"¡ìd, ServerTPS: ¡ìe"+tps);
					return true;
				default:
					return true;
				}
			}else if(args.length == 0) {
				sender.sendMessage("¡ìaSmartAI status ¡ìb>> ¡ìdLast sync task usage time: ¡ìe"+lastSynctaskTime+"ms¡ìd, Last async task usage time: ¡ìe"+lastASynctaskTime+"ms¡ìd, Now have ¡ìe"+limitList.size()+" mobs ¡ìdin control list, Mobs AI status now: ¡ìr"+getAIMode()+"¡ìd, ServerTPS: ¡ìe"+tps);
				return true;
			}else {
				return true;
			}
		}else {
			return false;
		}
	}
	@Override
	public void onDisable() {
			for (UUID entityUUID : limitList) {
				Entity mob = Bukkit.getEntity(entityUUID);
				LivingEntity livingMob = null;
				if(mob instanceof LivingEntity) {
					try {
						livingMob = (LivingEntity)mob;
						toggleAI(livingMob, true);
					} catch (Exception e) {
					}
					
				}
		}
	}
	private String getAIMode() {
		if(disableAI) {
			return "¡ìcLow TPS(Disabled AI)";
		}else {
			return "¡ìaHigh TPS(Enabled AI)";
		}
	}
	@EventHandler
	public void chunkLoad(ChunkLoadEvent e) {
		for (Entity entity : e.getChunk().getEntities()) {
			if(entityTypeCheck(entity)) {
				limitList.add(entity.getUniqueId());
			}
		}
	}
	@EventHandler
	public void chunkUnLoad(ChunkUnloadEvent e) {
		for (Entity entity : e.getChunk().getEntities()) {
			if(entityTypeCheck(entity)) {
				try {
					limitList.remove(entity.getUniqueId());
					toggleAI(((LivingEntity)entity), true);
				} catch (Exception e2) {
				}
			}
		}
	}
	@EventHandler
	public void worldUnLoad(WorldUnloadEvent e) {
		Chunk[] chunks = e.getWorld().getLoadedChunks();
		for (Chunk chunk : chunks) {
			for (Entity entity : chunk.getEntities()) {
				if(entityTypeCheck(entity)) {
					try {
						limitList.remove(entity.getUniqueId());
						toggleAI(((LivingEntity)entity), true);
					} catch (Exception e2) {
					}
				}
			}
		}
	}
	@EventHandler
	public void entitySpawn(EntitySpawnEvent e) {
		if(entityTypeCheck(e.getEntity())) {
			if(entityTypeCheck(e.getEntity())) {
				limitList.add(e.getEntity().getUniqueId());
			}
		}
		
	}
	public boolean entityTypeCheck(Entity entity) {
		List<String> listmob  = getConfig().getStringList("entity");
		String entityname = entity.getType().name();
		for (String string : listmob) {
			if(string.equals(entityname) && entity instanceof LivingEntity) {
				return true;
			}
		}
		return false;
	}
	private Class<?> getNMSClass(String className) {
        try {
            return Class.forName("net.minecraft.server." + version + "." + className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

	public String getTPS(int time) {
		try {
			double[] tps = ((double[]) tpsField.get(serverInstance));

			if (tps[time] > 20) {
				tps[time] = 20;
			}
			
			return format.format(tps[time]);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	public void debug(String message) {
		if(debug) {
			getLogger().info(message);
		}
	}
}
