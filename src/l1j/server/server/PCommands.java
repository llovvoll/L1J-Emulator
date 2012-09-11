/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.L1DatabaseFactory;
import l1j.server.server.datatables.SkillTable;
import l1j.server.server.model.L1Teleport;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.L1SkillUse;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.templates.L1Skill;
import l1j.server.server.utils.SQLUtil;

import static l1j.server.server.model.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.skill.L1SkillId.LIGHT;
import static l1j.server.server.model.skill.L1SkillId.SHIELD;
import static l1j.server.server.model.skill.L1SkillId.HOLY_WEAPON;
import static l1j.server.server.model.skill.L1SkillId.DECREASE_WEIGHT;
import static l1j.server.server.model.skill.L1SkillId.PHYSICAL_ENCHANT_DEX;
import static l1j.server.server.model.skill.L1SkillId.PHYSICAL_ENCHANT_STR;
import static l1j.server.server.model.skill.L1SkillId.BLESS_WEAPON;
import static l1j.server.server.model.skill.L1SkillId.HASTE;
import static l1j.server.server.model.skill.L1SkillId.GREATER_HASTE;
import static l1j.server.server.model.skill.L1SkillId.BERSERKERS;
import static l1j.server.server.model.skill.L1SkillId.IMMUNE_TO_HARM;
import static l1j.server.server.model.skill.L1SkillId.ABSOLUTE_BARRIER;
import static l1j.server.server.model.skill.L1SkillId.ADVANCE_SPIRIT;
import static l1j.server.server.model.skill.L1SkillId.STORM_SHOT;
import static l1j.server.server.model.skill.L1SkillId.EARTH_SKIN;
import static l1j.server.server.model.skill.L1SkillId.NATURES_TOUCH;

public class PCommands {
	private static Logger _log = Logger.getLogger(PCommands.class.getName());
	private static PCommands _instance;
	
	private static int[] PowerBuffSkills = { LIGHT, DECREASE_WEIGHT, 
		PHYSICAL_ENCHANT_DEX, PHYSICAL_ENCHANT_STR, BLESS_WEAPON, GREATER_HASTE,
		BERSERKERS, IMMUNE_TO_HARM, ABSOLUTE_BARRIER, ADVANCE_SPIRIT,
		STORM_SHOT, EARTH_SKIN, NATURES_TOUCH };

	private static int[] BuffSkills = { LIGHT, SHIELD, HOLY_WEAPON,
		DECREASE_WEIGHT, PHYSICAL_ENCHANT_DEX, PHYSICAL_ENCHANT_STR,
		BLESS_WEAPON, HASTE };

	// Starting experiment to see whether caching common messages has an
	// effect.
	private static final S_SystemMessage DropHelp =
		new S_SystemMessage("-drop all|mine|party on|off");
	private static final S_SystemMessage CommandsHelp =
		new S_SystemMessage("-warp 1-7, -karma, -buff, -bug, -drop, -help");
	private static final S_SystemMessage CommandsHelpNoBuff =
		new S_SystemMessage("-warp 1-7, -karma, -bug, -drop, -help");
	private static final S_SystemMessage NoBuff =
		new S_SystemMessage("The -buff command is disabled.");
	private static final S_SystemMessage BuffLevel =
		new S_SystemMessage("You must be level 45 to use -buff.");
	private static final S_SystemMessage NoWarpArea =
		new S_SystemMessage("You cannot -warp in this area.");
	private static final S_SystemMessage NoWarpState =
		new S_SystemMessage("You cannot -warp in your current state.");
	private static final S_SystemMessage NoWarp =
		new S_SystemMessage("The -warp command is disabled.");
	private static final S_SystemMessage NoPowerBuff =
		new S_SystemMessage("The -pbuff command is disabled.");
	private static final S_SystemMessage WarpLimit =
		new S_SystemMessage("-warp 1-7 only.");
	private static final S_SystemMessage WarpHelp =
		new S_SystemMessage("-warp 1-Pandora, 2-SKT, 3-Giran, 4-Werldern, 5-Oren, 6-Orc Town, 7-Silent Cavern");
	private static final S_SystemMessage BugHelp =
		new S_SystemMessage("-bug bugReport");
	private static final S_SystemMessage BugThanks =
		new S_SystemMessage("Bug reported. Thank you for your help!");
	private static final S_SystemMessage NotDK =
		new S_SystemMessage("Only Dragon Knights can use -dkbuff.");
	private static final S_SystemMessage DKHelp =
		new S_SystemMessage("You have to equip Helm of Magic to use -dkbuff.");
	private static final S_SystemMessage NoMp =
		new S_SystemMessage("You don't have enough mana to use -dkbuff.");
	private static final S_SystemMessage NoDkBuff =
		new S_SystemMessage("The -dkbuff command is disabled.");

	private PCommands() { }

	public static PCommands getInstance() {
		if (_instance == null) {
			_instance = new PCommands();
		}
		return _instance;
	}

	public void handleCommands(L1PcInstance player, String cmd2) {
		try {
			if (cmd2.equalsIgnoreCase("help")) {
				showPHelp(player);
			} else if (cmd2.startsWith("buff")) {
				buff(player);
			} else if (cmd2.startsWith("dkbuff")) {
				dkbuff(player);
			} else if (cmd2.startsWith("warp")) {
				warp(player, cmd2);
			} else if (cmd2.startsWith("pbuff")){
				powerBuff(player);
			} else if (cmd2.startsWith("bug")){
				reportBug(player, cmd2);
			} else if(cmd2.startsWith("karma")){
				checkKarma(player);
			} else if (cmd2.startsWith("drop")) {
				setDropOptions(player, cmd2);
			}
			_log.info(player.getName() + " used " + cmd2);
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	public void showPHelp(L1PcInstance player) {
		player.sendPackets(Config.PLAYER_BUFF && Config.PLAYER_COMMANDS
				? CommandsHelp : CommandsHelpNoBuff);
	}

	public void buff(L1PcInstance player){
		if (!Config.PLAYER_BUFF || !Config.PLAYER_COMMANDS) {
			player.sendPackets(NoBuff);
			return;
		}

		int level = player.getLevel();
		if (level < 45) {
			player.sendPackets(BuffLevel);
			return;
		}
		int max = 3;
		/* out for WK temporarily.
		if (level >= 60)
			max = BuffSkills.length;
		else if (level >= 55)
			max = 5;
		else if (level >= 50)
			max = 4;
			*/

		L1SkillUse skillUse = new L1SkillUse();
		for (int i = 0; i < max; i++)
			skillUse.handleCommands(player, BuffSkills[i], player.getId(),
					player.getX(), player.getY(), null, 0, 
					L1SkillUse.TYPE_SPELLSC);
	}
	
	public void dkbuff(L1PcInstance player){
		if (!Config.DK_BUFF || !Config.PLAYER_COMMANDS) {
			player.sendPackets(NoBuff);
			return;
		}

		if (!player.isDragonKnight()) {
			player.sendPackets(NotDK);
			return;
		}
			
		if (player.getCurrentMp() < 25) {
			player.sendPackets(NoMp);
			return;
		}
				
		L1SkillUse skillUse = new L1SkillUse();
		if (player.getInventory().checkEquipped(20013)) {
			skillUse.handleCommands(player, PHYSICAL_ENCHANT_DEX,
					player.getId(), player.getX(), player.getY(), null, 0, 
					L1SkillUse.TYPE_NORMAL);
		} else if (player.getInventory().checkEquipped(20015)) {
			skillUse.handleCommands(player, PHYSICAL_ENCHANT_STR,
					player.getId(), player.getX(), player.getY(), null, 0, 
					L1SkillUse.TYPE_NORMAL);
		} else
			player.sendPackets(DKHelp);
	}

	public void powerBuff(L1PcInstance player) {
		if (Config.POWER_BUFF && Config.PLAYER_COMMANDS) {
			L1SkillUse skillUse = new L1SkillUse();
			for (int i = 0; i < PowerBuffSkills.length; i++)
				skillUse.handleCommands(player, PowerBuffSkills[i],
						player.getId(), player.getX(), player.getY(), null, 0,
						L1SkillUse.TYPE_SPELLSC);
		} else if (Config.PLAYER_COMMANDS && !Config.POWER_BUFF)
			player.sendPackets(NoPowerBuff);
	}

	public void warp(L1PcInstance player, String cmd2) {
		if (!Config.WARP) {
			player.sendPackets(NoWarp);
			return;
		}
		
		if (!player.getLocation().getMap().isEscapable()) {
			player.sendPackets(NoWarpArea);
			return;
		}
		
		if (player.isPrivateShop() || player.hasSkillEffect(EARTH_BIND) || 
				player.isParalyzed() || player.isPinkName() || 
				player.isSleeped() || player.isDead() || 
				player.getMapId() == 99) {
			player.sendPackets(NoWarpState);
			return;
		}	
			
		try {
			int i = Integer.parseInt(cmd2.substring(5));
			if (i >= 1 && i <= 7 ) {
				Thread.sleep(3000);
				switch (i) {
					case 1: // Pandora
						L1Teleport.teleport(player, 32644, 32955, (short) 0, 5, true);
						break;
					case 2: // SKT
						L1Teleport.teleport(player, 33080, 33392, (short) 4, 5, true);
						break;
					case 3: // Giran
						L1Teleport.teleport(player, 33442, 32797, (short) 4, 5, true);
						break;
					case 4: // Weldern
						L1Teleport.teleport(player, 33705, 32504, (short) 4, 5, true);
						break;
					case 5: // Oren
						L1Teleport.teleport(player, 34061, 32276, (short) 4, 5, true);
						break;
					case 6: // Orc Town
						L1Teleport.teleport(player, 32715, 32448, (short) 4, 5, true);
						break;
					case 7: // Silent Cave
						L1Teleport.teleport(player, 32857, 32898, (short) 304, 5, true);
				}
			} else {
				player.sendPackets(WarpLimit);
			}
		} catch (Exception exception) {
			player.sendPackets(WarpHelp);
		}
	}

	private void reportBug(L1PcInstance pc, String bug) {
		Connection con = null;
		PreparedStatement pstm = null;
		
		try {
			bug = bug.substring(3).trim();
			if(bug.equals("")) {
				pc.sendPackets(BugHelp);
				return;
			}
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("INSERT INTO bugs (bugtext, charname, mapID, mapX, mapY, resolved) VALUES (?, ?, ?, ?, ?, 0);");
			pstm.setString(1, bug);
			pstm.setString(2, pc.getName());
			pstm.setInt(3, pc.getMapId());
			pstm.setInt(4, pc.getX());
			pstm.setInt(5, pc.getY());
			pstm.execute();
			pc.sendPackets(BugThanks);
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage("Could not report bug: ('" + bug +
						"', '" + pc.getName() + "', " + pc.getMapId() + ", " +
						pc.getX() + ", " + pc.getY() + ");"));
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	private void checkKarma(L1PcInstance pc) {
		pc.sendPackets(new S_SystemMessage("Your karma is currently: " + 
					pc.getKarma() + "."));
	}
	
	private void setDropOptions(final L1PcInstance pc, final String options) {
		List<String> pieces = Arrays.asList(options.split("\\s"));
		boolean on = pieces.get(2).equals("on");
		if (pieces.get(1).equals("all")) {
			pc.setDropMessages(on);
			pc.setPartyDropMessages(on);
		} else if (pieces.get(1).equals("party")) {
			pc.setPartyDropMessages(on);
		} else if (pieces.get(1).equals("mine")) {
			pc.setDropMessages(on);
		} else {
			pc.sendPackets(DropHelp);
		}
	}
}
