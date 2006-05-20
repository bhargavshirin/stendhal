package games.stendhal.server.entity.npc;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import games.stendhal.server.*;
import games.stendhal.server.entity.Player;
import games.stendhal.server.entity.item.*;
import games.stendhal.server.rule.*;
import marauroa.common.*;
import marauroa.common.game.*;
import marauroa.server.game.*;
import org.apache.log4j.Logger;

public class Behaviours {
	/** the logger instance. */
	private static final Logger logger = Log4J.getLogger(Behaviours.class);

	private static RPServerManager rpman;

	private static StendhalRPRuleProcessor rules;

	private static StendhalRPWorld world;

	public static void initialize(RPServerManager rpman, StendhalRPRuleProcessor rules, RPWorld world) {
		Behaviours.rpman = rpman;
		Behaviours.rules = rules;
		Behaviours.world = (StendhalRPWorld) world;
	}

	public static void addGreeting(SpeakerNPC npc) {
		addGreeting(npc, "Greetings! How may I help you?", null);
	}

	public static void addGreeting(SpeakerNPC npc, String text) {
		addGreeting(npc, text, null);
	}

	public static void addGreeting(SpeakerNPC npc, String text,
			SpeakerNPC.ChatAction action) {
		npc.add(ConversationStates.IDLE, new String[] { "hi", "hello", "greetings", "hola" }, ConversationStates.ATTENDING,
				text, action);

		npc.addWaitMessage(null, new SpeakerNPC.ChatAction() {
			public void fire(Player player, String text, SpeakerNPC engine) {
				engine.say("Please wait! I am attending "
						+ engine.getAttending().getName() + ".");
			}
		});
	}

	/**
	 * Makes the given NPC say a text when it hears a certain trigger during
	 * a conversation.
	 * @param npc The NPC that should reply
	 * @param trigger The text that causes the NPC to answer
	 * @param text The answer
	 */
	public static void addReply(SpeakerNPC npc, String trigger, String text) {
		npc.add(ConversationStates.ATTENDING, trigger, null, ConversationStates.ATTENDING, text, null);
	}

	/**
	 * Makes the given NPC say a text when it hears one of the given triggers
	 * during a conversation.
	 * @param npc The NPC that should reply
	 * @param triggers The texts that cause the NPC to answer
	 * @param text The answer
	 */
	public static void addReply(SpeakerNPC npc, String[] triggers, String text) {
		npc.add(ConversationStates.ATTENDING, triggers, ConversationStates.ATTENDING, text, null);
	}

	/**
	 * @deprecated not used anymore.
	 */
	public static void addReply(SpeakerNPC npc, List<String> triggers,
			String text) {
		addReply(npc, triggers.toArray(new String[2]), text);
	}

	public static void addQuest(SpeakerNPC npc, String text) {
		npc.add(ConversationStates.ATTENDING, new String[] { "quest", "task" }, ConversationStates.ATTENDING, text, null);
	}

	public static void addQuest(SpeakerNPC npc, String[] texts) {
		npc.add(ConversationStates.ATTENDING, new String[] { "quest", "task" }, ConversationStates.ATTENDING, texts, null);
	}

	public static void addJob(SpeakerNPC npc, String jobDescription) {
		addReply(npc, new String[] { "job", "work" }, jobDescription);
	}

	public static void addHelp(SpeakerNPC npc, String helpDescription) {
		addReply(npc, new String[] { "help", "ayuda" }, helpDescription);
	}

	public static void addGoodbye(SpeakerNPC npc) {
		addGoodbye(npc, "Bye.");
	}

	public static void addGoodbye(SpeakerNPC npc, String text) {
		npc.addByeMessage(text, null);
		npc.add(ConversationStates.ANY, new String[] { "bye", "farewell", "cya", "adios" }, ConversationStates.IDLE,
				text, null);
	}

	public static class SellerBehaviour {
		protected Map<String, Integer> items;

		protected String choosenItem;

		protected int amount;

		public SellerBehaviour() {
			this.items = new HashMap<String, Integer>();
		}

		public SellerBehaviour(Map<String, Integer> items) {
			this.items = items;
		}

		public Set<String> getItems() {
			return items.keySet();
		}

		public boolean hasItem(String item) {
			return items.containsKey(item);
		}

		public int getPrice(String item) {
			return items.get(item);
		}

		public void setChoosenItem(String item) {
			choosenItem = item;
		}

		public String getChoosenItem() {
			return choosenItem;
		}

		public void setAmount(String text) {
			try {
				amount = Integer.parseInt(text);
			} catch (Exception e) {
				amount = 1;
			}
		}

		public int getAmount() {
			return amount;
		}

		public int playerMoney(Player player) {
			int money = 0;

			Iterator<RPSlot> it = player.slotsIterator();
			while (it.hasNext()) {
				RPSlot slot = it.next();
				for (RPObject object : slot) {
					if (object instanceof Money) {
						money += ((Money) object).getQuantity();
					}
				}
			}

			return money;
		}

		public boolean chargePlayer(Player player, int amount) {
			int left = amount;

			Iterator<RPSlot> it = player.slotsIterator();
			while (it.hasNext() && left != 0) {
				RPSlot slot = it.next();

				Iterator<RPObject> object_it = slot.iterator();
				while (object_it.hasNext()) {
					RPObject object = object_it.next();
					if (object instanceof Money) {
						int quantity = ((Money) object).getQuantity();
						if (left >= quantity) {
							slot.remove(object.getID());
							left -= quantity;

							object_it = slot.iterator();
						} else {
							((Money) object).setQuantity(quantity - left);
							left = 0;
							break;
						}
					}
				}
			}

			world.modify(player);

			return left == 0;
		}

		public boolean onSell(SpeakerNPC seller, Player player,
				String itemName, int amount, int itemPrice) {
			EntityManager manager = world.getRuleManager().getEntityManager();

			Item item = manager.getItem(itemName);
			if (item == null) {
				logger.error("Trying to sell an unexisting item: " + itemName);
				return false;
			}

			if (item instanceof StackableItem) {
				((StackableItem) item).setQuantity(amount);
			} else {
				amount = 1;
			}

			item.put("zoneid", player.get("zoneid"));
			IRPZone zone = world.getRPZone(player.getID());
			zone.assignRPObjectID(item);

			if (player.equip(item)) {
				chargePlayer(player, itemPrice * amount);
				seller.say("Congratulations! Here is your " + itemName + "!");
				return true;
			} else {
				seller.say("Sorry, but you cannot equip the " + itemName + ".");
				return false;
			}
		}
	}

	public static void addSeller(SpeakerNPC npc, SellerBehaviour items) {
		addSeller(npc, items, true);
	}

	public static void addSeller(SpeakerNPC npc, SellerBehaviour items,
			boolean offer) {
		npc.setBehaviourData("seller", items);

		StringBuffer st = new StringBuffer();
		for (String item : items.getItems()) {
			st.append(item + ", ");
		}

		if (offer) {
			npc.add(ConversationStates.ATTENDING,
					"offer",
					null,
					ConversationStates.ATTENDING,
					"I sell " + st.toString(),
					null);
		}

		npc.add(ConversationStates.ATTENDING,
				"buy",
				null,
				20,
				null,
				new SpeakerNPC.ChatAction() {
					public void fire(Player player, String text, SpeakerNPC engine) {
						SellerBehaviour sellableItems = (SellerBehaviour) engine
								.getBehaviourData("seller");
		
						// find out what the player wants to buy, and how
						// much of it
						String[] words = text.split(" ");
		
						String amount = "1";
						String item = null;
						if (words.length > 2) {
							amount = words[1].trim();
							item = words[2].trim();
						} else if (words.length > 1) {
							item = words[1].trim();
						}
		
						// find out if the NPC sells this item, and if so,
						// how much it costs.
						if (sellableItems.hasItem(item)) {
							sellableItems.setChoosenItem(item);
							sellableItems.setAmount(amount);
		
							int price = sellableItems.getPrice(item)
									* sellableItems.getAmount();
		
							engine.say(amount + " " + item + " costs " + price
									+ ". Do you want to buy?");
						} else {
							engine.say("Sorry, I don't sell " + item);
							engine.setActualState(ConversationStates.ATTENDING);
						}
					}
				});

		npc.add(20,
				"yes",
				null,
				ConversationStates.ATTENDING,
				"Thanks.",
				new SpeakerNPC.ChatAction() {
					public void fire(Player player, String text, SpeakerNPC engine) {
						SellerBehaviour sellableItems = (SellerBehaviour) engine
								.getBehaviourData("seller");
		
						String itemName = sellableItems.getChoosenItem();
						int itemPrice = sellableItems.getPrice(itemName);
						int itemAmount = sellableItems.getAmount();
		
						if (sellableItems.playerMoney(player) < itemPrice * itemAmount) {
							engine.say("A real pity! You don't have enough money!");
							return;
						}
		
						logger.debug("Selling a " + itemName + " to player "
								+ player.getName());
		
						sellableItems.onSell(engine, player, itemName, itemAmount,
								itemPrice);
					}
				});

		npc.add(20,
				"no",
				null,
				ConversationStates.ATTENDING, "Ok, how may I help you?",
				null);
	}

	public static class BuyerBehaviour {
		private Map<String, Integer> items;

		private String choosenItem;
		
		private int amount;

		public BuyerBehaviour(Map<String, Integer> items) {
			this.items = items;
		}

		public Set<String> getItems() {
			return items.keySet();
		}

		public boolean hasItem(String item) {
			return items.containsKey(item);
		}

		public int getPrice(String item) {
			return items.get(item);
		}

		public void setChoosenItem(String item) {
			choosenItem = item;
		}

		public String getChoosenItem() {
			return choosenItem;
		}

		public void setAmount(String text) {
			try {
				amount = Integer.parseInt(text);
			} catch (Exception e) {
				amount = 1;
			}
		}

		public int getAmount() {
			return amount;
		}
		
		public void payPlayer(Player player, int amount) {
			boolean found = false;
			Iterator<RPSlot> it = player.slotsIterator();
			while (it.hasNext()) {
				RPSlot slot = it.next();

				Iterator<RPObject> object_it = slot.iterator();
				while (object_it.hasNext()) {
					RPObject object = object_it.next();
					if (object instanceof Money) {
						((Money) object).add(amount);
						found = true;
					}
				}
			}

			if (!found) {
				RPSlot slot = player.getSlot("bag");
				Money money = new Money(amount);
				slot.assignValidID(money);
				slot.add(money);
			}

			world.modify(player);
		}

		/**
		 * Tries to remove some of the items of the given type from a slot.
		 * If there are not enough items, 
		 * @param slot The slot 
		 * @param itemName
		 * @param amount
		 * @return
		 */
		private boolean removeItem(RPSlot slot, String itemName, int amount) {
			// First iterate over the slot to count the number of available
			// items with the given itemName; we must make sure that enough
			// are available to fulfil the requested amount.
			// To avoid a second iteration, we store items that can later be
			// removed in a LinkedList.
			int count = 0;
			LinkedList<RPObject> toBeRemoved = new LinkedList<RPObject>();
			
			Iterator<RPObject> countIterator = slot.iterator();
			while (countIterator.hasNext() && count < amount) {
				RPObject object = countIterator.next();
				if (object instanceof Item
						&& object.get("name").equals(itemName)) {
					count++;
					toBeRemoved.add(object);
				}
			}
			if (count < amount) {
				// not enough items in the slot. Don't change anything.
				return false;
			} else {
				for (RPObject object: toBeRemoved) {
					slot.remove(object.getID());
				}
			}
			return true;
		}

		public boolean removeItem(Player player, String itemName, int amount) {
			/* We iterate first the bag */
			if (removeItem(player.getSlot("bag"), itemName, amount)) {
				world.modify(player);
				return true;
			}

			Iterator<RPSlot> it = player.slotsIterator();
			while (it.hasNext()) {
				RPSlot slot = it.next();

				if (removeItem(slot, itemName, amount)) {
					world.modify(player);
					return true;
				}
			}

			return false;
		}

		public boolean onBuy(SpeakerNPC seller, Player player, String itemName,
				int amount, int itemPrice) {
			if (removeItem(player, itemName, amount)) {
				payPlayer(player, itemPrice * amount);
				seller.say("Thanks! Here is your money.");
				return true;
			} else {
				seller.say("Sorry! You don't have enough " + itemName + ".");
				return false;
			}
		}
	}

	public static void addBuyer(SpeakerNPC npc, BuyerBehaviour items) {
		addBuyer(npc, items, true);
	}

	public static void addBuyer(SpeakerNPC npc, BuyerBehaviour items,
			boolean offer) {
		npc.setBehaviourData("buyer", items);

		StringBuffer st = new StringBuffer();
		for (String item : items.getItems()) {
			st.append(item + ", ");
		}

		if (offer) {
			npc.add(ConversationStates.ATTENDING,
					"offer",
					null,
					ConversationStates.ATTENDING,
					"I buy " + st.toString(),
					null);
		}

		npc.add(ConversationStates.ATTENDING,
				"sell",
				null,
				30,
				null,
				new SpeakerNPC.ChatAction() {
					public void fire(Player player, String text, SpeakerNPC engine) {
						BuyerBehaviour buyableItems = (BuyerBehaviour) engine
								.getBehaviourData("buyer");
		
						String[] words = text.split(" ");
		
						String amount = "1";
						String item = null;
						if (words.length > 2) {
							amount = words[1].trim();
							item = words[2].trim();
						} else if (words.length > 1) {
							item = words[1].trim();
						}
		
						if (buyableItems.hasItem(item)) {
							buyableItems.setChoosenItem(item);
							buyableItems.setAmount(amount);
							int price = buyableItems.getPrice(item)
							* buyableItems.getAmount();
		
		
							engine.say(amount + " " + item + " is worth " + price
									+ ". Do you want to sell?");
						} else {
							engine.say("Sorry, I don't buy " + item);
							engine.setActualState(ConversationStates.ATTENDING);
						}
					}
				});

		npc.add(30,
				"yes",
				null,
				ConversationStates.ATTENDING,
				"Thanks.",
				new SpeakerNPC.ChatAction() {
					public void fire(Player player, String text, SpeakerNPC engine) {
						BuyerBehaviour buyableItems = (BuyerBehaviour) engine
								.getBehaviourData("buyer");
		
						String itemName = buyableItems.getChoosenItem();
						int itemPrice = buyableItems.getPrice(itemName);
						int itemAmount = buyableItems.getAmount();
		
						logger.debug("Buying " + itemAmount + " " + itemName + " from player "
								+ player.getName());
		
						buyableItems.onBuy(engine, player, itemName, itemAmount, itemPrice);
					}
				});

		npc.add(30,
				"no",
				null,
				ConversationStates.ATTENDING,
				"Ok, how may I help you?",
				null);
	}

	public static class HealerBehaviour extends SellerBehaviour {
		public HealerBehaviour(int cost) {
			super();
			items.put("heal", cost);
		}

		public void heal(Player player, SpeakerNPC engine) {
			player.setHP(player.getBaseHP());
			player.healPoison();
			world.modify(player);
		}
	}

	public static void addHealer(SpeakerNPC npc, int cost) {
		npc.setBehaviourData("healer", new HealerBehaviour(cost));

		npc.add(ConversationStates.ATTENDING,
				"offer",
				null,
				ConversationStates.ATTENDING,
				"I heal",
				null);
		
		npc.add(ConversationStates.ATTENDING, "heal", null, 40, null, new SpeakerNPC.ChatAction() {
			public void fire(Player player, String text, SpeakerNPC engine) {
				HealerBehaviour healer = (HealerBehaviour) engine
						.getBehaviourData("healer");
				int cost = healer.getPrice("heal");

				if (cost > 0) {
					engine.say("Healing costs " + cost
							+ ". Do you want to pay?");
				} else {
					engine.say("You are healed. How may I help you?");
					healer.heal(player, engine);

					engine.setActualState(ConversationStates.ATTENDING);
				}
			}
		});

		npc.add(40,
				"yes",
				null,
				ConversationStates.ATTENDING,
				"Thanks.",
				new SpeakerNPC.ChatAction() {
					public void fire(Player player, String text, SpeakerNPC engine) {
						HealerBehaviour healer = (HealerBehaviour) engine
								.getBehaviourData("healer");
						int itemPrice = healer.getPrice("heal");
		
						if (healer.playerMoney(player) < itemPrice) {
							engine.say("A real pity! You don't have enough money!");
							return;
						}
						healer.chargePlayer(player, itemPrice);
						// TODO: where does the NPC heal the player?
					}
				});

		npc.add(40,
				"no",
				null,
				ConversationStates.ATTENDING,
				"OK, how may I help you?",
				null);
		}
}