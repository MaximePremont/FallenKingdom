package net.samagames.fallenkingdom.game;

import net.minecraft.server.v1_9_R2.BlockFenceGate;
import net.samagames.api.SamaGamesAPI;
import net.samagames.api.games.Game;
import net.samagames.api.games.themachine.ICoherenceMachine;
import net.samagames.tools.scoreboards.ObjectiveSign;
import net.samagames.tools.scoreboards.TeamHandler;
import net.samagames.tools.scoreboards.VObjective;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.material.Door;
import org.bukkit.material.Gate;
import org.bukkit.material.TrapDoor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;

import static org.bukkit.Bukkit.getServer;

/*
 * This file is part of FallenKingdom.
 *
 * FallenKingdom is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FallenKingdom is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FallenKingdom.  If not, see <http://www.gnu.org/licenses/>.
 */
public class FGame extends Game<FPlayer> implements Listener{

    private FStage step = FStage.INIT;
    private HashMap<FTeamType, FTeam> teams = new HashMap<>();
    private HashMap<Player, FPlayer> players = new HashMap<>();
    private long timerSecs;
    private int timerTaskId;
    private ICoherenceMachine coherMach;
    private Location lobbyLoc = net.samagames.tools.LocationUtils.str2loc(SamaGamesAPI.get().getGameManager().getGameProperties().getConfigs().get("LobbyLoc").getAsString());

    public FGame() {
        super("fallenkingdom", "FallenKingdom", "Des royaumes qui tombent", FPlayer.class);
    }

    @Override
    public void startGame()
    {
        super.startGame();
        init();
    }

    private void init()
    {
        coherMach = getCoherenceMachine();
        teams.put(FTeamType.ROUGE, new FTeam(FTeamType.ROUGE));
        teams.put(FTeamType.VERT, new FTeam(FTeamType.VERT));
        teams.put(FTeamType.BLEU, new FTeam(FTeamType.BLEU));
        teams.put(FTeamType.JAUNE, new FTeam(FTeamType.JAUNE));
        teams.put(FTeamType.ORANGE, new FTeam(FTeamType.ORANGE));
        stepPlus(30);
        //TODO
    }

    private void stepPlus(long seconds)
    {
        timerSecs = seconds;
        timerTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(SamaGamesAPI.get().getPlugin(), new Runnable() {
            @Override
            public void run() {
                if(timerSecs < 0)
                {
                    getServer().getScheduler().cancelTask(timerTaskId);
                    if(step == FStage.MATCHMAKING)
                    {
                        step = FStage.PREP;
                        startPlay();
                    }
                    else if (step == FStage.PREP)
                    {
                        step = FStage.PVP;
                        startPvp();
                    }
                    else if(step == FStage.PVP)
                    {
                        step = FStage.BATTLE;
                        startBattle();
                    }
                    else
                    {
                        endGame();
                    }
                }
                else
                {
                    timerSecs--;
                    //TODO update scoreboard
                }
            }
        }, 0L, 20L);
    }

    @Override
    public void handleLogin(Player p)
    {
        super.handleLogin(p);
        players.put(p, new FPlayer(p));
        //coherMach.getMessageManager().writeGameStartIn((int)timerSecs).display(p);
        //TODO teleport player to lobby spawn
        for(FPlayer fp : players.values())
        {
            fp.getPlayer().teleport(lobbyLoc);
        }
    }

    @Override
    public void handleLogout(Player p)
    {
        super.handleLogout(p);
        //TODO
    }

    @Override
    public void handleReconnect(Player p)
    {
        super.handleReconnect(p);
        //TODO Check if prep. speed potion effect still active. If yes, remove it.
    }

    private void startPlay()
    {
        stepPlus(600);
        for(FPlayer fp : players.values())
        {
            fp.getPlayer().getInventory().clear();
            fp.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
        }
        for(FTeam ft : teams.values())
        {
            ft.tpPlayersToBase();
        }
        coherMach.getMessageManager().writeGameStart().displayToAll();
    }

    private void startPvp()
    {
        stepPlus(300);
        for(FPlayer fp : players.values())
        {
            fp.getPlayer().removePotionEffect(PotionEffectType.SPEED);
        }
        //coherMach.getMessageManager().writeCustomMessage()
    }

    private void startBattle()
    {
        stepPlus(1200);
    }

    public void endGame()
    {
        //TODO Choose winner team
        getServer().getScheduler().cancelTask(timerTaskId);
        //TODO stop tasks, rewards, etc.
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e)
    {
        switch (step) {
            case MATCHMAKING:
                e.setCancelled(true);
                break;
            default:
                if(getBlockZone(e.getPlayer(), e.getBlock()) == FZone.ENEMY)
                    e.setCancelled(true);
                break;
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e)
    {
        if(e.getBlock().getType() == Material.OBSIDIAN)
            e.setCancelled(true); //A DISCUTER
        switch (step) {
            case MATCHMAKING:
                e.setCancelled(true);
                break;
            case PREP:
            case PVP:
                if(e.getBlock().getType() == Material.TNT || getBlockZone(e.getPlayer(), e.getBlock()) != FZone.BASE)
                    e.setCancelled(true);
                break;
            case BATTLE:
                FZone zone = getBlockZone(e.getPlayer(), e.getBlock());
                if(zone == FZone.ENEMY || e.getBlock().getType() == Material.TNT && zone == FZone.BASE)
                    e.setCancelled(true);
                break;
        }
    }

    public void setPlayerTeam(Player p, FTeamType type)
    {
        teams.get(type).addPlayer(players.get(p));
    }

    public boolean isInPlayerTeamBase(Player p, Location loc)
    {
        return players.get(p).getTeam().isInBase(loc);
    }

    private FZone getBlockZone(Player p, Block blk)
    {
        if(players.get(p).getTeam().isInBase(blk.getLocation()))
            return FZone.BASE;
        else
        {
            for(FTeam ft : teams.values())
            {
                if(ft.isInBase(blk.getLocation()))
                    return FZone.ENEMY;
            }
            return FZone.WILD;
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e)
    {
        if(e.getEntity() instanceof Player && e.getDamager() instanceof Player) {
            FPlayer damager = players.get(e.getDamager());
            FPlayer player = players.get(e.getEntity());
            if (damager.getTeam() == player.getTeam()) {
                e.setCancelled(true);
                return;
            }
            switch (step) {
                case INIT:
                case PREP:
                    e.setCancelled(true);
                    break;
                case PVP:
                case BATTLE:
                    if(e.getDamage() > player.getHealth())
                        player.getTeam().addKillCoins(player);
                    break;
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e)
    {
        e.setKeepInventory(true);
        e.setKeepLevel(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e)
    {
        if(e.getClickedBlock() instanceof Door || e.getClickedBlock() instanceof TrapDoor || e.getClickedBlock() instanceof Gate) {
            final Block d = e.getClickedBlock();
            if (getBlockZone(e.getPlayer(), e.getClickedBlock()) == FZone.ENEMY)
                e.setCancelled(true);
            else
                getServer().getScheduler().runTaskLater(SamaGamesAPI.get().getPlugin(), new Runnable() {
                    @Override
                    public void run() {
                        if(d instanceof Door)
                            ((Door)d).setOpen(false);
                        else if(d instanceof  TrapDoor)
                            ((TrapDoor)d).setOpen(false);
                        else
                            ((Gate)d).setOpen(false);
                    }
                }, 40l);
        }
    }

    /*public void test()
    {
        net.samagames.tools.scoreboards.ObjectiveSign v = new ObjectiveSign("Matching", "Fallen Kingdom");
        net.samagames.tools.scoreboards.ObjectiveSign.RawObjective.
    }*/
}
