package main;

import java.util.*;
import java.io.*;
import java.math.*;

class Player {
	
    public static void main(String args[]) {
    	
        // LOAD GAME STATE AT THE START OF THE GAME
        GameState.load();

        // MAKE MOVES FOR EACH ROUND
        while (true) {
        	
            // DETERMINE NEXT COMMANDS FOR CURRENT ROUND
            AIManager.makeDecision();
            
            // EXECUTE COMMANDS AND END CURRENT ROUND
            CommandManager.executeCommands();

            // UPDATE GAME STATE FOR NEXT ROUND
            GameState.update();
            
        }
    }

}

class AIManager {

	/**
	 * Determines the actions for the current round and relays those to the CommandManager.
	 */
    public static void makeDecision() {

        if (GameState.round == 0) { // FIRST ROUND
            makeActionsForFirstRound();
        } else { // ALL OF THE OTHER ROUNDS
        	makeActions();
        }
        
    }
    
    /**
     * Determines the best approach when it comes to conquering neutral cells close to the starting cell.
     * Might upgrade starting cell and currently only sends units to neutral cells with production and which can be conquered.
     * Neutral cells are scored based on their starting productivity and defending units inside. The more production and the less units, the better.
     */
	private static void makeActionsForFirstRound() {
		
    	// CLOSEST HALF OF ALL NEUTRAL CELLS TO THE STARTING CELL PLUS NEUTRAL CELL(S) IN THE MIDDLE
    	ArrayList<Cell> neutralNeighbors = GameState.neutralCells;
    	neutralNeighbors.sort(new Comparator<Cell>() {
			@Override
			public int compare(Cell c1, Cell c2) {
				// sorts by best production value based on defending stationary units of the cell
				int prod1 = c1.production;
				int prod2 = c2.production;
				
				int units1 = c1.units;
				int units2 = c2.units;

				int distance1 = GameState.getDistanceBetweenCells(GameState.ownStartingCell, c1);
				int distance2 = GameState.getDistanceBetweenCells(GameState.ownStartingCell, c2);

				double score1 = ((double) (prod1*6)-distance1-units1);
				double score2 = ((double) (prod2*6)-distance2-units2);

//				double score1 = ((double) (prod1*(1/distance1)-units1));
//				double score2 = ((double) (prod2*(1/distance2)-units2));

				if (score1 < score2) {
					return 1;
				} else if (score1 > score2) {
					return -1;
				} else {
					return 0;
				}
				
			}
		});
    	
    	// REMAINING UNITS LEFT TO USE IN STARTING CELL
    	int availableUnits = GameState.ownStartingCell.units;

    	// GET DISTANCE TO ENEMY STARTING CELL
    	int distanceToEnemy = GameState.getDistanceBetweenCells(GameState.ownStartingCell, GameState.enemyStartingCell);
    	
    	// DETERMINE IF UPGRADING IS MORE PROFITABLE BECAUSE OF A LOT OF NEUTRAL UNITS
    	int remainingUnitsAgainstNeutralCells = availableUnits - (GameState.neutralTotalUnits/2);
    	boolean alreadyUpgraded = false;
    	if (GameState.neutralTotalProduction > 1 && remainingUnitsAgainstNeutralCells < 10) {
    		// A LOT OF NEUTRAL UNITS: DONT CONQUER THOSE WITH MANY NEUTRAL UNITS; UPGRADE PROD OR ATTACK ENEMY INSTEAD (maybe send units close to the enemy to be ready to attack, and see what he does (if he burns his units on neutral units, then attack, otherwise upgrade)
    		if (availableUnits >= 10 && distanceToEnemy > 13) { // no early bomb threat; enough rounds to yield profit in units
    			CommandManager.increaseProductivity(GameState.ownStartingCell.id);
    			availableUnits -= 10;
    			alreadyUpgraded = true;
    		}
    	}
    	
    	for (Cell neutralCell : neutralNeighbors) {
    		
    		if (availableUnits == 0) {
    			break; // no units available anymore
    		}
    		
    		int neutralUnits = neutralCell.units;
    		
    		if (availableUnits <= neutralUnits) {
    			continue; // not enough units to conquer this cell
    		}
    		
    		if (neutralCell.production == 0) { 
    			continue; // ignore neutral cell with no production
    		}
    		
    		// send enough units to take over the cell to start producing more units
    		int distanceToOwnStartingCell = GameState.getDistanceBetweenCells(neutralCell, GameState.ownStartingCell);
    		int distanceToEnemyStartingCell = GameState.getDistanceBetweenCells(neutralCell, GameState.enemyStartingCell);
    		if (distanceToOwnStartingCell == distanceToEnemyStartingCell) {
        		CommandManager.neutralAttack(GameState.ownStartingCell.id, neutralCell.id, 1);
        		CommandManager.neutralAttack(GameState.ownStartingCell.id, neutralCell.id, 1, 1);
        		availableUnits -= 2;
    		} else if (distanceToOwnStartingCell < distanceToEnemyStartingCell) {
        		CommandManager.neutralAttack(GameState.ownStartingCell.id, neutralCell.id, neutralUnits+1);
        		availableUnits -= neutralUnits+1;
    		}	
		}
    	
    	// UPGRADE PRODUCTIVITY IF ENOUGH UNITS ARE STILL AVAILABLE AND IT IS SAVE TO DO SO
    	if (!alreadyUpgraded && GameState.neutralCells.size() > 10 && availableUnits >= 10 && distanceToEnemy > 13) { // no early bomb threat; enough rounds to yield profit in units
			CommandManager.increaseProductivity(GameState.ownStartingCell.id);
			availableUnits -= 10;
		}
    	
    	// SEND BOMB IF ENEMY STARTING CELL HAS PRODUCTIVITY
    	if (GameState.enemyStartingCell.production == 3) {
    		CommandManager.sendBomb(GameState.ownStartingCell.id, GameState.enemyStartingCell.id);
    	}
    	
    	// TODO idea: FIGURE OUT MOST EFFICIENT START THAT YIELDS MORE UNITS AFTER THE FIRST 15 ROUNDS? MIGHT BE DANGEROUS IF IT THEN DOES NOT SEND UNITS AT ALL because staying at the starting amount yields the best result :S
    }
	
    private static void makeActions() {
    	
    	// SEND BOMB TO ENEMY STARTING CELL IF BOMB WAS NOT SEND YET AND ENEMY UPGRADED EARLY PRODUCTION
    	if (GameState.ownBombsAvailable == 2) {
    		if (GameState.enemyStartingCell.production == 3 && GameState.enemyStartingCell.owner == -1) {
        		CommandManager.sendBomb(GameState.enemyStartingCell.getClosestCellWithOwner(1).id, GameState.enemyStartingCell.id);
    		} else {
        		int worthwhileProduction = 3;
        		if (GameState.round > 30 || GameState.ownTotalUnits+50 < GameState.enemyTotalUnits) {
        		    worthwhileProduction = 2;
        		}
        		if (GameState.round > 40) {
        			worthwhileProduction = 1;
        		}
    			for (Cell cell : GameState.enemyCells) {
    				if (cell.production == worthwhileProduction) {
    					CommandManager.sendBomb(cell.getClosestCellWithOwner(1).id, cell.id);
    					break;
    				}
    			}
    		}
    	}
    	
    	// MAKE SURE NEUTRAL CELLS ARE BEING CONQUERED
    	for (Cell cell : GameState.neutralCells) {
    		if (cell.incomingTotalFriendlyUnits > 0 && cell.incomingTotalEnemyUnits > 0 && !cell.isAboutToBeConquered(cell.getDistanceToClosestCellWithOwner(1))) {
    			Cell friendlyCell = cell.getClosestCellWithOwner(1);
    			if (friendlyCell != null) {
        			CommandManager.neutralAttack(friendlyCell.id, cell.id, 1);
        			CommandManager.log("Additonal neutral attack: " + cell.id);
    			}
    		}
    	}
    	
    	
    	
    	// DETERMINE STANDARD ATTACK FOR ALL FRIENDLY CELLS
    	ArrayList<Cell> enemyCells = new ArrayList<Cell>(GameState.getEnemyCells());
    	if (enemyCells.size() > 0) {
    		
    		// Sort by closest to friendly cell
    		enemyCells.sort(new Comparator<Cell>() {
				@Override
				public int compare(Cell c1, Cell c2) {
					int prod1 = c1.production;
					int prod2 = c2.production;
					
					int units1 = c1.units;
					int units2 = c2.units;

					int distance1 = c1.getDistanceToClosestCellWithOwner(1);
					int distance2 = c2.getDistanceToClosestCellWithOwner(1);

					double score1 = ((double) (prod1*6)-distance1);
					double score2 = ((double) (prod2*6)-distance2);

//					double score1 = ((double) (prod1*(1/distance1)));
//					double score2 = ((double) (prod2*(1/distance2)));
					
					if (score1 < score2) {
						return 1;
					} else if (score1 > score2) {
						return -1;
					} else {
						return 0;
					}
//					return c1.getDistanceToClosestCellWithOwner(1) - c2.getDistanceToClosestCellWithOwner(1);
				}
			});
    		
    		
    		// select the closest enemy cell
    		Cell targetedEnemyCell = enemyCells.get(0);
    		
    		CommandManager.log("Attack: " + targetedEnemyCell.id);
    		
    		// select the friendly cell closest to the targeted enemy cell
    		Cell closestFriendlyCellToTargetedEnemyCell = targetedEnemyCell.getClosestCellWithOwner(1);
    		
    		if (closestFriendlyCellToTargetedEnemyCell != null) {
    			// determine the distance
        		int distanceToEachOther = GameState.getDistanceBetweenCells(closestFriendlyCellToTargetedEnemyCell, targetedEnemyCell);
        		
        		// attack with bomb
        		int worthwhileProduction = 3;
        		if (GameState.round > 20 || GameState.ownTotalUnits+20 < GameState.enemyTotalUnits) {
        		    worthwhileProduction = 2;
        		}
        		
        		if (GameState.round >= 4 && GameState.ownBombsAvailable > 0 && !targetedEnemyCell.isAboutToBeConquered(distanceToEachOther) && targetedEnemyCell.production == worthwhileProduction  && !targetedEnemyCell.isBombGoingToOverlapWithOtherBomb(closestFriendlyCellToTargetedEnemyCell)) {
        			CommandManager.sendBomb(closestFriendlyCellToTargetedEnemyCell.id, targetedEnemyCell.id);
        		} else if (GameState.round >= 10 && GameState.ownBombsAvailable > 0) {
        			for (Cell cell : GameState.enemyCells) {
        				if (cell.production == 3) {
        					Cell friendlyCell = cell.getClosestCellWithOwner(1);
        					if (friendlyCell != null && !cell.isAboutToBeConquered(GameState.getDistanceBetweenCells(cell, friendlyCell)) && !cell.isBombGoingToOverlapWithOtherBomb(friendlyCell)) {
        						CommandManager.sendBomb(friendlyCell.id, cell.id);
        						CommandManager.specialAttack(friendlyCell.id, cell.id, 1, 1);
        						break;
        					}
        				}
        			}
        		}
        		
        		for (Cell cell : GameState.getFriendlyCells()) {
        			
        			if (cell.units == 0) {
        				continue; // no units available
        			}
        			
        			// determine cell to send units to
    			    int to = GameState.routingTable[cell.id][targetedEnemyCell.id];
    			    if (to == -1) { // if no intermediate path is available, ...
    			        to = targetedEnemyCell.id; // ... take direct path
    			    }
    			    
    			    // check if bomb impact is expected at the cell
    		        if (!GameState.getCellByID(to).isTroopGoingToArriveOnBombImpact(cell)) {
    		        	
    		        	// send units
        			    CommandManager.standardAttack(cell.id, to, cell.units);
    			    }
        		}
    		}
    	}
    	
    	
    	// STANDARD BUFFER
		for (Cell cell : GameState.getFriendlyCells()) {
			Cell enemyCell = cell.getClosestCellWithOwner(-1);
			if (enemyCell != null) {
				int distance = GameState.getDistanceBetweenCells(cell, enemyCell);
				int unitsNeeded = enemyCell.units+enemyCell.production-distance*(cell.productionDisabled > 0 ? 0 : cell.production)-cell.incomingFriendlyUnits[1]+enemyCell.incomingEnemyUnits[1];
				if (unitsNeeded > 0) {
					CommandManager.saveUnitsForDefense(cell.id, unitsNeeded);
				}
			}
		}
    	
    	
    	// DEFEND CELLS
    	for (Cell cell : GameState.getFriendlyCells()) {
    		switch (cell.threatStatus) {
    		case BEING_CONQUERED:
    			if (cell.production >= 1 && GameState.ownTotalProduction > GameState.enemyTotalProduction) {
        			int requiredUnits = cell.getUnitsRequiredToSaveThisRoundToDefendSuccessfully();
        			for (Neighbor neighbor : cell.neighbors) {
        				if (neighbor.getCell().owner == 1 && neighbor.getCell().threatStatus == ThreatStatus.SAFE) {
        					CommandManager.sendUnitsForDefense(neighbor.getCell().id, cell.id, requiredUnits);
        					requiredUnits -= neighbor.getCell().units;
        				}
        				if (requiredUnits <= 0) {
        					break;
        				}
        			}
    			}
    			// fall-through
    			
    		case DEFEND_BY_INCOMING_UNITS:
    			CommandManager.saveUnitsForDefense(cell.id, 100);
    			break;
    			
    		case DEFEND_BY_SAVING_UNITS:
    			CommandManager.log("Save Units: " + cell.id + " " + cell.getUnitsRequiredToSaveThisRoundToDefendSuccessfully());
    			CommandManager.saveUnitsForDefense(cell.id, cell.getUnitsRequiredToSaveThisRoundToDefendSuccessfully()); 
    			break;

    		case SAFE:
    			// do nothing
    			// fall-through
    			
    		default:
    			// do nothing
    		}
    	}
    	
    	
    	// FIND ADDITIONAL NEUTRAL CELLS TO CONQUER
    	if ((GameState.round < 10 && GameState.ownTotalProduction < GameState.enemyTotalProduction) || GameState.ownTotalProduction > GameState.enemyTotalProduction+2 || GameState.ownTotalUnits > GameState.enemyTotalUnits+50 || GameState.round > 40) {
    		ArrayList<Cell> neutralCells = new ArrayList<Cell>(GameState.getNeutralCells());
        	// sort by cells furthest away from enemy cells
    		neutralCells.sort(new Comparator<Cell>() {
				@Override
				public int compare(Cell c1, Cell c2) {
					// sorts by best production value based on defending stationary units of the cell
					int prod1 = c1.production;
					int prod2 = c2.production;
					
					int units1 = c1.units;
					int units2 = c2.units;

					int distance1 = c1.getDistanceToClosestCellWithOwner(1);
					int distance2 = c2.getDistanceToClosestCellWithOwner(1);

//					double score1 = ((double) (prod1*6)-distance1-units1);
//					double score2 = ((double) (prod2*6)-distance2-units2);

					double score1 = ((double) (prod1*(1/distance1)-units1));
					double score2 = ((double) (prod2*(1/distance2)-units2));

					if (score1 < score2) {
						return 1;
					} else if (score1 > score2) {
						return -1;
					} else {
						return 0;
					}
					
				}
			});
    		
    		
    		for (Cell cell : neutralCells) {
    			int distance = cell.getDistanceToClosestCellWithOwner(1);
    			if (cell.production > 0 && distance <= 8 && !cell.isAboutToBeConquered(distance) && distance <= cell.getDistanceToClosestCellWithOwner(-1)) {
    				CommandManager.neutralAttack(cell.getClosestCellWithOwner(1).id, cell.id, Math.min(cell.getClosestCellWithOwner(1).production, cell.units+1));
					break;
    			}
    		}
    	}
    	
    	// UPGRADE
    	if (GameState.ownTotalProduction > GameState.enemyTotalProduction+2 || GameState.ownTotalUnits > GameState.enemyTotalUnits+50) {
    		ArrayList<Cell> ownCells = new ArrayList<Cell>(GameState.getFriendlyCells());
        	// sort by cells furthest away from enemy cells
    		ownCells.sort(new Comparator<Cell>() {
				@Override
				public int compare(Cell cell1, Cell cell2) {
				    int distance1 = cell1.getDistanceToClosestCellWithOwner(-1);
				    int distance2 = cell2.getDistanceToClosestCellWithOwner(-1);
//				    return distance2-distance1;
				    if (distance1 < distance2) {
				        return 1;
				    } else if (distance1 > distance2) {
				        return -1;
				    } else {
				        return 0;
				    }
				}
			});
    		
    		
    		for (Cell cell : ownCells) {
    			if (cell.production < 3 && (cell.production >= 1 || cell.units >= 10) && cell.threatStatus == ThreatStatus.SAFE && !cell.isHitByBomb() && !cell.isBombImpactExpectedIn(10+(int)(10f/cell.production))) {
					CommandManager.increaseProductivity(cell.id);
					CommandManager.log("Upgrade: " + cell.id);
					break;
    			}
    		}
    	}
    	
    	// SPECIAL STRAT
//    	for (Cell cell : GameState.neutralCells) {
//    		
//    		if (cell.production > 0 && cell.units > 0) {
//    			
//    			Cell friendlyCell = cell.getClosestCellWithOwner(1);
//    			Cell enemyCell = cell.getClosestCellWithOwner(-1);
//    			
//    			if (friendlyCell == null || enemyCell == null) {
//    				continue;
//    			}
//    			
//    			int distanceToOwnCell = GameState.getDistanceBetweenCells(cell, friendlyCell);
//    			int distanceToEnemyCell = GameState.getDistanceBetweenCells(cell, enemyCell);
//    			
//    			if (distanceToOwnCell < distanceToEnemyCell) {
//    				
//    				for (int i = 0; i < 20; i++) {
//    					
//    					if (GameState.round-i < 0) {
//    						break;
//    					}
//    					
//        				for (Troop troop : GameState.history.troopsLaunched.get(GameState.round-i)) {
//        					if (troop.to == cell.id && GameState.round+1+distanceToOwnCell == troop.arrival) {
//        						if (friendlyCell.commands.get(0).specialAttack != null) {
//	        						CommandManager.specialAttack(friendlyCell.id, cell.id, 1);
//	        						CommandManager.specialAttack(friendlyCell.id, cell.id, 1, 1);
//        						}
//        					}
//        				}
//    				}
//    			}
//				if (friendlyCell.commands.get(0).specialAttack == null) {
//    				CommandManager.saveUnitsForSpecialAttack(friendlyCell.id, 2);
//    			}
//    		}
//    	}
    	
    	for (Cell cell : GameState.neutralCells) {
    		
    		if (cell.production > 0 && cell.units > 0 && cell.incomingTotalFriendlyUnits == 0 && cell.incomingTotalEnemyUnits > 0) {

        		Cell friendlyCell = cell.getClosestCellWithOwner(1);
        		
        		if (friendlyCell == null) {
        			continue;
        		}
        		
        		if (friendlyCell.commands.get(0).specialAttack != null) {
        			continue;
        		}
        		
        		int distanceToOwnCell = GameState.getDistanceBetweenCells(friendlyCell, cell);
        		
    			for (int i = 0; i < 20; i++) {
					
					if (GameState.round-i < 0) {
						break;
					}
					
    				for (Troop troop : GameState.history.troopsLaunched.get(GameState.round-i)) {
    					if (troop.owner == -1 && troop.to == cell.id && GameState.round+distanceToOwnCell+1 == troop.arrival) {
    						int remainingUnitsAfterFight = troop.units-cell.units;
    						if (remainingUnitsAfterFight > 0 && friendlyCell.units >= remainingUnitsAfterFight) {
        						CommandManager.specialAttack(friendlyCell.id, cell.id, remainingUnitsAfterFight);
        						//CommandManager.specialAttack(friendlyCell.id, cell.id, 1, 1);
        						CommandManager.log("Intercepting: " + cell.id);
    						}
    						break;
    					}
    				}
				}
    			
    		}
    	}
    	
    	// SEND UNITS AWAY IF CELL IS UNDER ATTACK
    	for (Cell cell : GameState.friendlyCells) {
    		if (cell.isBombImpactExpected()) { // bomb is expected
    			if (cell.isBombImpactExpectedNextRound() && cell.units > 0) { // potential impact is expected next round
					CommandManager.evacuateUnits(cell.id);
    			}
    		}
    	}

    }
    
}



/**
 * Contains all relevant information about the current game in progress.
 * That includes information about all cells, their distances to each other, the current round, (sorted) lists of cells grouped by team, starting cells, starting areas, and much more
 */
class GameState {

    // STATIC DATA (CREATED ONCE ON GAME START)
	private static Scanner scanner = null;

	public static Cell[] cells;
    public static int cellCount;
    public static boolean isEvenCellAmount;

    public static int[][] distances; // cell distances to each other
    public static int[][] routingTable;
    
    public static Cell ownStartingCell;
    public static Cell enemyStartingCell;
    
    public static ArrayList<Cell> ownArea; // the cells around the own starting cell
    public static ArrayList<Cell> enemyArea; // the cells around the enemy starting cell
    public static ArrayList<Cell> centerArea; // the cells which are right in the middle of both starting cells
    
    
    // DYNAMIC DATA (UPDATED EACH ROUND)
    public static int round;
    
    public static int ownBombsAvailable;
    public static int enemyBombsAvailable;

    public static ArrayList<Cell> friendlyCells;
    public static ArrayList<Cell> enemyCells;
    public static ArrayList<Cell> neutralCells;

    public static ArrayList<Cell> friendlyCellsByProduction;
    public static ArrayList<Cell> enemyCellsByProduction;
    public static ArrayList<Cell> neutralCellsByScore;
    
    public static int ownTotalUnits;
    public static int enemyTotalUnits;
    public static int neutralTotalUnits;
    
    public static int ownTotalProduction;
    public static int enemyTotalProduction;
    public static int neutralTotalProduction;
    
    
    // OTHERS
    public static History history;
    private static boolean updateCellLists;
    
    /**
     * Loads and sets all information at the start of a new game. 
     */
    public static void load() {
        if (scanner != null) {
            return;
        }

        round = 0;

        ownBombsAvailable = 2;
        enemyBombsAvailable = 2;
        
        ownTotalUnits = 0;
        enemyTotalUnits = 0;
        neutralTotalUnits = 0;
        
        ownTotalProduction = 0;
        enemyTotalProduction = 0;
        neutralTotalProduction = 0;
        
        history = new History();
        updateCellLists = false;

        // INIT SCANNER TO LOAD DATA FROM THE GAME IN PROGRESS
        scanner = new Scanner(System.in);

        // LOAD CELL AND CONNECTION COUNT
        cellCount = scanner.nextInt(); // the number of cells
        isEvenCellAmount = (cellCount % 2 == 0);
        
        // LOAD CELL CONNECTIONS
        int linkCount = scanner.nextInt(); // the number of links between cells
        distances = new int[cellCount][cellCount];
        for (int i = 0; i < linkCount; i++) {
            int cell1 = scanner.nextInt();
            int cell2 = scanner.nextInt();
            int distance = scanner.nextInt();
            distances[cell1][cell2] = distance;
            distances[cell2][cell1] = distance;
        }
        
        // LOAD CELL INFORMATION (IDs, OWNER, UNITS, PRODUCTION)
        cells = new Cell[cellCount];
        int entityCount = scanner.nextInt(); // the number of cells
        for (int i = 0; i < entityCount; i++) {
            int[] args = new int[5]; 
            // 						 0     1     2     3     4
            // entityID, entityType, arg1, arg2, arg3, arg4, arg5
            int entityID = scanner.nextInt();
            String entityType = scanner.next();
            for (int j = 0; j < 5; j++) {
                args[j] = scanner.nextInt();
            }
            if (entityType.equals("FACTORY")) { // CELL
                cells[entityID] = new Cell(entityID, args[0], args[1], args[2]);
                // Cell: entityID, owner (friendly 1, enemy -1, neutral 0), unit amount, production
                if (args[0] == 1) { // OWN
            		ownTotalUnits += args[1];
            		ownTotalProduction += args[2];
            	} else if (args[0] == -1) { // ENEMY
             		enemyTotalUnits += args[1];
            		enemyTotalProduction += args[2];
            	} else { // NEUTRAL
            		neutralTotalUnits += args[1];
            		neutralTotalProduction += args[2];
            	}
            }
            // NO TROOPS OR BOMBS WERE SEND ON GAME START YET
        }
        
        // SETUP CELL LISTS BY TEAM
        updateCellLists();
        
        // SETUP CELL LISTS BY TEAM SORTED BY PRODUCTION
        updateSortedCellLists();
        
        // DETERMINE STARTING CELLS
        ownStartingCell = GameState.getFriendlyCells().get(0);
    	enemyStartingCell = GameState.getEnemyCells().get(0);
        
        // DETERMINE (STARTING AND CENTER) AREAS FOR EACH PLAYER
        ownArea = new ArrayList<Cell>();
        enemyArea = new ArrayList<Cell>();
        centerArea = new ArrayList<Cell>();
        
        int halfCellCount = (GameState.cellCount/2);
        int counter = 0;
        while (counter < halfCellCount) {
        	ownArea.add(ownStartingCell.neighbors.get(counter).getCell());
        	enemyArea.add(enemyStartingCell.neighbors.get(counter).getCell());
        	counter++;
        }
        for (Cell cell : ownArea) {
        	if (enemyArea.contains(cell)) {
        		centerArea.add(cell);
        	}
        }
        
        // CREATE ROUTING TABLE
        routingTable = createRoutingTable();
    }

	/**
     * Updates the game state for the next round by reading in the up-to-date information of the game in progress.
     * Make sure you call 'CommandManager.executeCommands()' before calling this function first.
     */
    public static void update() {
        round++;
        
        ownTotalUnits = 0;
        enemyTotalUnits = 0;
        neutralTotalUnits = 0;
        
        ownTotalProduction = 0;
        enemyTotalProduction = 0;
        neutralTotalProduction = 0;
        
        //long time = System.currentTimeMillis();
        
        int updateCount = scanner.nextInt(); // the number of active entities currently in the game
        for (int i = 0; i < updateCount; i++) {
        	int[] args = new int[5]; 
            // 						 0     1     2     3     4
            // entityID, entityType, arg1, arg2, arg3, arg4, arg5
            int entityID = scanner.nextInt();
            String entityType = scanner.next();
            for (int j = 0; j < 5; j++) {
                args[j] = scanner.nextInt();
            }
            if (entityType.equals("FACTORY")) { // CELL
                updateCell(entityID, args[0], args[1], args[2], args[3]);
                // Cell: entityID, owner (friendly 1, enemy -1, neutral 0), unit amount, production, productionDisabledForRoundAmount
            } else if (entityType.equals("TROOP")) { // UNITS
                addTroop(entityID, args[0], args[1], args[2], args[3], args[4]);
                // Troop: entityID, owner (friendly 1, enemy -1, neutral 0), from cell, to cell, unit amount, remaining rounds till troop arrives
            } else if (entityType.equals("BOMB")) { // BOMB
                addBombLaunch(entityID, args[0], args[1], args[2], args[3]);
                // Bomb: entityID, owner, from cell, (to cell), (remaining rounds till impact)
            }
        }
        
        
        //CommandManager.log("Update: " + (System.currentTimeMillis() - time));
        
        // UPDATE CELL LISTS IF ONE OR MORE CELLS CHANGED OWNERSHIP LAST ROUND
        if (updateCellLists) {
        	updateCellLists();
        	updateSortedCellLists();
        	updateCellLists = false;
            //CommandManager.log("Cells: " + (System.currentTimeMillis() - time));
        }
        

        // DETERMINE THE THREAT LEVEL TO THIS CELL BASED ON ENEMY TROOPS
        for (Cell cell : friendlyCells) {
        	cell.determineThreat();
        	if (cell.threatStatus != ThreatStatus.SAFE) {
            	CommandManager.log("ThreatStatus: " + cell.id + " " + cell.threatStatus);
        	}
        }
    }

    
    private static void updateCell(int cellID, int owner, int units, int production, int roundsDisabled) {
    	// update cell each round
    	cells[cellID].update(owner, units, production, roundsDisabled);
    	
    	// sum up stationary units and production of each cell for each player each round
    	if (owner == 1) { // OWN
    		ownTotalUnits += units;
    		ownTotalProduction += production; // TODO adds 0 if roundsDisabled > 0; might mess with upgrade strategy!!!
    	} else if (owner == -1) { // ENEMY
     		enemyTotalUnits += units;
    		enemyTotalProduction += production; // TODO adds 0 if roundsDisabled > 0; might mess with upgrade strategy!!!
    	} else { // NEUTRAL
    		neutralTotalUnits += units;
    		neutralTotalProduction += production;
    	}
    }

    private static void addTroop(int troopID, int owner, int from, int to, int units, int distanceRemaining) {
    	// only add new unit troops once when they are launched, not on the later rounds while mid-travel
    	if (!history.troopIDs.contains(troopID)) { 
    		history.troopsLaunched.get(round).add(new Troop(troopID, owner, from, to, units, round, round+distanceRemaining)); // TODO double check if round values are correct!
    		history.troopIDs.add(troopID);
        	// add and map out incoming units for the cell
    		cells[to].addIncomingUnits(owner, units, distanceRemaining);
        }
    	
    	// sum up units of each player each round
    	if (owner == 1) {
    		ownTotalUnits += units;
    	} else {
    		enemyTotalUnits += units;
    	}
    }

    private static void addBombLaunch(int bombID, int owner, int from, int to, int distance) {
        if (!history.bombIDs.contains(bombID)) {
        	Bomb bomb = new Bomb(bombID, owner, from, to, round, round+distance);
        	history.bombs.add(bomb);
        	history.bombIDs.add(bombID);
        	registerNewBombLaunch(bomb);
        	if (owner == 1) {
        		ownBombsAvailable--;
        	} else {
        		enemyBombsAvailable--;
        	}
        }
    }
    
	public static void registerNewBombLaunch(Bomb bomb) {
        if (bomb.owner == 1) { // PLAYER BOMB
        	GameState.getCellByID(bomb.to).addIncomingBomb(bomb);
        } else if (bomb.owner == -1) { // ENEMY BOMB LAUNCH
        	for (Cell cell : GameState.cells) {
        		cell.addIncomingBomb(bomb);
        	}
        }
    }
	
	public static void reportBombImpact(Cell cell) {
		Bomb bomb = null;
		
		if (cell.bombImpacts.size() == 1) {
			bomb = cell.bombImpacts.get(0).bomb;
		} else if (cell.bombImpacts.size() >= 2) {
			for (PredictedBombImpact bombImpact : cell.bombImpacts) {
				if (bombImpact.predictedImpact == GameState.round) {
					if (bomb != null) {
						return; // More than one bomb were expected to hit this round. Cant determine which bomb hit and which one might be still traveling.
					}
					bomb = bombImpact.bomb;
				}
			}
		}
		
		if (bomb == null) {
			return; // No bomb found?!
		}
		
		for (Cell c : cells) {
			c.removeIncomingBomb(bomb);
		}
	}
    
    public static Cell getCellByID(int cellID) {
		return cells[cellID];
	}


    public static int getDistanceBetweenCells(int cellID1, int cellID2) {
        return distances[cellID1][cellID2];
    }
    
    public static int getDistanceBetweenCells(Cell cell1, Cell cell2) {
    	return getDistanceBetweenCells(cell1.id, cell2.id);
    }


    public static ArrayList<Cell> getFriendlyCells() {
        return friendlyCells;
    }

    public static ArrayList<Cell> getEnemyCells() {
        return enemyCells;
    }

    public static ArrayList<Cell> getNeutralCells() {
        return neutralCells;
    }


    public static ArrayList<Cell> getFriendlyCellsByProduction() {
    	return friendlyCellsByProduction;
    }

    public static ArrayList<Cell> getEnemyCellsByProduction() {
    	return enemyCellsByProduction;
    }

    public static ArrayList<Cell> getNeutralCellsByScore() {
    	return neutralCellsByScore;
    }
    
    
    private static void updateCellLists() {
        friendlyCells = new ArrayList<Cell>();
        enemyCells = new ArrayList<Cell>();
        neutralCells = new ArrayList<Cell>();
    	
        for (Cell cell : cells) {
        	if (cell.owner == 1) {
        		friendlyCells.add(cell);
        	} else if (cell.owner == -1) {
        		enemyCells.add(cell);
        	} else {
        		neutralCells.add(cell);
        	}
        }
    }
    
    private static void updateSortedCellLists() {
        friendlyCellsByProduction = new ArrayList<Cell>(friendlyCells);
        enemyCellsByProduction = new ArrayList<Cell>(enemyCells);
        neutralCellsByScore = new ArrayList<Cell>(neutralCells);
        
        friendlyCellsByProduction.sort(new Comparator<Cell>() {
        	@Override
        	public int compare(Cell cell1, Cell cell2) {
        		return cell1.production - cell2.production;
        	}
		});
        enemyCellsByProduction.sort(new Comparator<Cell>() {
        	@Override
        	public int compare(Cell cell1, Cell cell2) {
        		return cell1.production - cell2.production;
        	}
		});
        neutralCellsByScore.sort(new Comparator<Cell>() {
			@Override
			public int compare(Cell c1, Cell c2) {
				// sorts by best production value based on defending stationary units of the cell
				int prod1 = c1.production;
				int prod2 = c2.production;
				
				int units1 = c1.units;
				int units2 = c2.units;
				
				double score1 = ((double) prod1 / units1);
				double score2 = ((double) prod2 / units2);
				
				if (score1 > score2) {
					return 1;
				} else if (score1 < score2) {
					return -1;
				} else {
					return 0;
				}
			}
		});
    }
    

	public static void ownershipChanged() {
		updateCellLists = true;
	}
	
	/**
	 * Creates the routing table with the Floyd-Warshall algorithm
	 */
	private static int[][] createRoutingTable() {
		
		// FROM-TO TABLES
		int[][] distanceTable = new int[cellCount][cellCount];
		int[][] routingTable = new int[cellCount][cellCount];
		
		// INIT
		final int MAX_NEIGHBOR_DISTANCE = 7; // limits neighbors to the closest ones
		for (int i = 0; i < cellCount; i++) {
			for (int j = 0; j < cellCount; j++) {
				int distance = getDistanceBetweenCells(i, j);
				if (i == j) {
					distanceTable[i][j] = 100;
					routingTable[i][j] = -1;
				} else if (distance <= MAX_NEIGHBOR_DISTANCE) {
					distanceTable[i][j] = distance;
					routingTable[i][j] = j;
				} else {
					distanceTable[i][j] = 100;
					routingTable[i][j] = -1;
				}
			}
		}
		
		// FLOYD-WARSHALL
		for (int k = 0; k < cellCount; k++) {
			for (int i = 0; i < cellCount; i++) {
				for (int j = 0; j < cellCount; j++) {
					
					if (i <= j || i == k || j == k) continue;
					
					int directDistance = distanceTable[i][j];
					int intermediateDistance = distanceTable[i][k] + distanceTable[k][j];
					
					if (intermediateDistance < directDistance) {
						distanceTable[i][j] = intermediateDistance;
						distanceTable[j][i] = intermediateDistance;
						routingTable[i][j] = routingTable[i][k];
						routingTable[j][i] = routingTable[j][k];
					}
					
				}
			}
		}
		
		
		return routingTable;
	}
}

class Cell {

    int id;
    int owner;
    int units;
    int production;
    int productionDisabled;

	int[] incomingUnits;
	int[] incomingFriendlyUnits;
	int[] incomingEnemyUnits;
	int incomingTotalEnemyUnits;
	int incomingTotalFriendlyUnits;
    ArrayList<PredictedBombImpact> bombImpacts;
    
    ArrayList<Neighbor> neighbors;
    
    LinkedList<RoundCommands> commands;
    
    ThreatStatus threatStatus;
    
    
    public Cell(int id, int owner, int units, int production) {
        this.id = id;
        this.owner = owner;
        this.units = units;
        this.production = production;
        this.productionDisabled = 0;
        
        this.threatStatus = ThreatStatus.SAFE;

		this.incomingUnits = new int[21];
		this.incomingFriendlyUnits = new int[21];
		this.incomingEnemyUnits = new int[21];
		this.incomingTotalEnemyUnits = 0;
		this.incomingTotalFriendlyUnits = 0;
		this.bombImpacts = new ArrayList<PredictedBombImpact>(4);
        
        this.neighbors = new ArrayList<Neighbor>();
        for (int i = 0; i < GameState.cellCount; i++) {
        	if (i != this.id) {
        		this.neighbors.add(new Neighbor(i, GameState.getDistanceBetweenCells(this.id, i)));
        	}
        }
        this.neighbors.sort(null); // uses .compareTo of Neighbor (natural ordering: closest to farthest)
       
		commands = new LinkedList<RoundCommands>();
		for (int i = 0; i < 21; i++) {
			commands.add(new RoundCommands(id));
		}
    }


	public void update(int owner, int units, int production, int productionDisabled) {
        if (productionDisabled == 5) {
        	GameState.reportBombImpact(this);
        }
        
        if (this.owner != owner) {
        	GameState.ownershipChanged();
        }

        this.owner = owner;
        this.units = units;
        this.productionDisabled = productionDisabled;
        if (productionDisabled == 0) {
        	this.production = production;
        }
        
        // SIMULATE TROOPS MOVING CLOSER TO THIS CELL
        incomingTotalEnemyUnits -= incomingEnemyUnits[0];
        incomingTotalFriendlyUnits -= incomingFriendlyUnits[0];
        for (int i = 0; i < incomingUnits.length-1; i++) {
        	incomingUnits[i] = incomingUnits[i+1];
        	incomingFriendlyUnits[i] = incomingFriendlyUnits[i+1];
        	incomingEnemyUnits[i] = incomingEnemyUnits[i+1];
        }
        incomingUnits[incomingUnits.length-1] = 0;
        incomingFriendlyUnits[incomingFriendlyUnits.length-1] = 0;
        incomingEnemyUnits[incomingEnemyUnits.length-1] = 0;
        
        bombImpacts.removeIf(bombImpact -> bombImpact.predictedImpact < GameState.round); // impact should have already occured
    }

    /**
     * Adds an incoming unit troop.
     * @param owner The player who owns the units of the troop (1 for friendly, -1 for hostile)
     * @param units The amount of units in the troops.
     * @param distance The distance in rounds until the troop arrives to this cell.
     */
    public void addIncomingUnits(int owner, int units, int distance) {
        incomingUnits[distance] += owner*units;
        if (owner == 1) {
            incomingTotalFriendlyUnits += units;
            incomingFriendlyUnits[distance] += units;
        } else {
        	incomingTotalEnemyUnits += units;
            incomingEnemyUnits[distance] += units;
        }
    }
    
    public void addIncomingBomb(Bomb bomb) {
    	if (bomb.owner == 1) {
        	bombImpacts.add(new PredictedBombImpact(bomb, bomb.impact));
    	} else {
    	    if (bomb.owner == 0 && production <= 1) {
    	        return; // probability low
    	    }
            int impact = GameState.round + GameState.getDistanceBetweenCells(bomb.from, this.id);
    		bombImpacts.add(new PredictedBombImpact(bomb, impact));
    	}
    }
    
    public void removeIncomingBomb(Bomb bomb) {
    	for (PredictedBombImpact bombImpact : bombImpacts) {
    		if (bombImpact.bomb.id == bomb.id) {
    			bombImpacts.remove(bombImpact);
    			return;
    		}
    	}
    }
    

    
    
    
    
    public boolean isBombImpactExpected() {
    	return (bombImpacts.size() > 0);
    }
    
    public boolean isBombImpactExpectedNextRound() {
    	return isBombImpactExpectedIn(1);
    }
    
    public boolean isBombImpactExpectedIn(int rounds) {
    	for (PredictedBombImpact bombImpact : bombImpacts) {
    		if (bombImpact.predictedImpact == GameState.round+rounds) {
    			return true;
    		}
    	}
    	return false;
    }
    
    public boolean isBombImpactExpectedInLessThan(int rounds) {
    	for (PredictedBombImpact bombImpact : bombImpacts) {
    		if (bombImpact.predictedImpact < GameState.round+rounds) {
    			return true;
    		}
    	}
    	return false;
    }
    
    public boolean isBombImpactExpectedInMoreThan(int rounds) {
    	for (PredictedBombImpact bombImpact : bombImpacts) {
    		if (bombImpact.predictedImpact > GameState.round+rounds) {
    			return true;
    		}
    	}
    	return false;
    }
    
    public boolean isBombGoingToOverlapWithOtherBomb(Cell cell) {
        
        if (bombImpacts.size() == 0) {
            return false;
        }
        
        int distance = GameState.getDistanceBetweenCells(cell, this);
        int impact = GameState.round + distance + 1;
        
        for (PredictedBombImpact bombImpact : bombImpacts) {
            if ((bombImpact.predictedImpact-5) <= impact && impact <= (bombImpact.predictedImpact+5)) {
                return true;
            }
        }
        
        return false;
    }
    
    public boolean isTroopGoingToArriveOnBombImpact(Cell cell) {
        
        if (bombImpacts.size() == 0) {
            return false;
        }
        
        int distance = GameState.getDistanceBetweenCells(cell, this);
        int arrival = GameState.round + distance + 1;
        
        for (PredictedBombImpact bombImpact : bombImpacts) {
            if (arrival == bombImpact.predictedImpact) {
                CommandManager.log("Prevented " + cell.id + " to " + this.id + " arrival in round " + arrival);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 
     * @return Returns true if this cell got hit by a bomb and its production is disabled, false otherwise.
     */
    public boolean isHitByBomb() {
    	return (productionDisabled != 0);
    }
    
    /**
     * @param distance The distance to this cell.
     * @return Returns true if this cell is about to be conquered by own units.
     */
    public boolean isAboutToBeConquered(int distance) {
    	
    	if (this.owner == 1) {
    		return true;
    	}
    	
    	int units = this.units;
    	for (int i = 1; i < incomingUnits.length; i++) {
    		int producedUnits;
    		if (owner == 0) {
    			producedUnits = 0;
    		} else {
    			producedUnits = (productionDisabled > 0 ? 0 : this.production);
    		}
    		units += incomingEnemyUnits[i] - incomingFriendlyUnits[i] + producedUnits;
    		if (units < 0) {
    			return true;
    		}
    	}
    	
    	return false;
    }
    
    
    
    public Cell findNearbySaveFriendlyCellToSendUnitsTo() { // TODO remove?
    	for (Neighbor neighbor : neighbors) {
    		if (neighbor.getCell().owner == 1) { // TODO check if this cell is about to fall into enemy hands
    			return neighbor.getCell();
    		}
    	}
		return null;
	}
	
	public Cell findSaveCellToEvacuateUnitsTo() {
	    
	    Cell saveCell = null;
	    
	    // SEND TO FRIENDLY CELL
	    for (Neighbor neighbor : neighbors) {
	        Cell cell = neighbor.getCell();
	        if (cell.owner == 1 && !cell.isBombImpactExpectedIn(GameState.getDistanceBetweenCells(this, cell)+1)) {
	            saveCell = cell;
	            break;
	        }
	    }
	    
	    // SEND TO NEUTRAL CELL
	    if (saveCell == null) {
	        for (Neighbor neighbor : neighbors) {
    	        Cell cell = neighbor.getCell();
    	        if (cell.owner == 0 && this.units > cell.units && !cell.isBombImpactExpectedIn(GameState.getDistanceBetweenCells(this, cell)+1)) {
    	            saveCell = cell;
    	            break;
    	        }
	        }
	    }
	    
	    // SEND TO ENEMY CELL
	    if (saveCell == null) {
	        for (Neighbor neighbor : neighbors) {
    	        Cell cell = neighbor.getCell();
    	        if (cell.owner == -1 && this.units > cell.units && !cell.isBombImpactExpectedIn(GameState.getDistanceBetweenCells(this, cell)+1)) {
    	            saveCell = cell;
    	            break;
    	        }
	        }
	    }
	    
	    // SEND TO RANDOM CELL
	    if (saveCell == null) {
	        int random = (int) (Math.random() * neighbors.size());
	        saveCell = neighbors.get(random).getCell();
	    }
	    
	    return saveCell;
	}
    
	
	
    public int getDistanceToClosestCellWithOwner(int owner) {
    	for (Neighbor neighbor : neighbors) {
    		if (neighbor.getCell().owner == owner) {
    			return neighbor.distance;
    		}
    	}
    	return 21;
    }
    
    public Cell getClosestCellWithOwner(int owner) {
    	for (Neighbor neighbor : neighbors) {
    		if (neighbor.getCell().owner == owner) {
    			return neighbor.getCell();
    		}
    	}
    	return null;
    }
    
    
    
    public void determineThreat() {
    	if (incomingTotalEnemyUnits > 0) { // if enemy troops are traveling to this cell
    		
    		int unitsWithSelfDefenseOnly = units;
    		int unitsWithFriendlyUnitsHelp = units;

    		boolean canDefendEachRoundWithBaseProductionAlone = true; // can defend by sending away remaining units. no threat
    		boolean canDefendWithProductionBySavingUnits = true; // can defend by the units produced at this cell when stopped sending them away
    		boolean canDefendWithProductionAndHelp = true; // can defend by the units produced and the current friendly troops coming in
    		
    		// THERE IS ALWAYS PRODUCED 'production' UNITS IN THE CELL
        	for (int i = 1; i < incomingUnits.length; i++) {
        		
        		int producedUnits = ((productionDisabled-(i-1)) > 0 ? 0 : production);
        		if (canDefendEachRoundWithBaseProductionAlone && incomingEnemyUnits[i] > (producedUnits+incomingFriendlyUnits[i])) {
        			canDefendEachRoundWithBaseProductionAlone = false; // more enemy units are attacking than friendly units are being produced
        		}

        		unitsWithSelfDefenseOnly += producedUnits - incomingEnemyUnits[i];
        		if (canDefendWithProductionBySavingUnits && unitsWithSelfDefenseOnly < 0) {
        			canDefendWithProductionBySavingUnits = false;
        		}

        		unitsWithFriendlyUnitsHelp += producedUnits - incomingEnemyUnits[i] + incomingFriendlyUnits[i];
        		if (canDefendWithProductionAndHelp && unitsWithFriendlyUnitsHelp < 0) {
        			canDefendWithProductionAndHelp = false;
        		}
        	}
        	
        	// choose the most effective defense measure required to keep the cell under control
        	if (canDefendEachRoundWithBaseProductionAlone) {
        		threatStatus = ThreatStatus.SAFE; // cell can defend itself each round via its own produced units because each enemy troop has less or equal amount of units (action: nothing, but there might not be any units left to send away, because they got killed by the enemy troop)
        	} else if (canDefendWithProductionBySavingUnits) {
        		threatStatus = ThreatStatus.DEFEND_BY_SAVING_UNITS; // cell can defend itself by saving the produced units across rounds (action: save units)
        	} else if (canDefendWithProductionAndHelp) {
        		threatStatus = ThreatStatus.DEFEND_BY_INCOMING_UNITS; // cell can be defended by it saving the produced units across rounds plus the units send by friendly cells (action: save units)
        	} else {
        		threatStatus = ThreatStatus.BEING_CONQUERED; // cell will be conquered by enemy (action: try to defend it by sending units from other friendly cell)
        	}
    	} else {
        	threatStatus = ThreatStatus.SAFE;
    	}
	}
    
    public int[] getRequiredUnitAmount() {
    	
    	int[] remainingUnits = new int[incomingUnits.length];
    	remainingUnits[0] = units;
    	

    	int[] requiredUnits = new int[incomingUnits.length];
    	requiredUnits[0] = 0;
    	int sendUnits = 0;
    	
    	for (int i = 1; i < incomingUnits.length; i++) {
    		// calculate own remaining stationary units
    		remainingUnits[i] = remainingUnits[i-1] + production + incomingFriendlyUnits[i] - incomingEnemyUnits[i];
    		
    		if (remainingUnits[i]+sendUnits >= 0) { // if friendly units are present then dont request units
            	requiredUnits[i] = 0;
    		} else { // if not enough friendly units are present to defend
    			requiredUnits[i] = -(remainingUnits[i]+sendUnits);
    			sendUnits += requiredUnits[i];
    		}
    	}
    	
    	return requiredUnits;
    }
    
    public int getUnitsRequiredToSaveThisRoundToDefendSuccessfully() {

    	int requiredUnits = 0;
    	for (int i = incomingUnits.length-1; i > 0; i--) {
    		int availableUnits = production + incomingFriendlyUnits[i] - incomingEnemyUnits[i];
			//CommandManager.log("" + id + " " + i + " " + availableUnits + " " + requiredUnits + " " + Math.max(0, (requiredUnits-availableUnits)));
			requiredUnits = Math.max(0, (requiredUnits-availableUnits));
    	}
    	
    	return requiredUnits;
    }
    
    public int[] getUnitsRequiredFromFriendlyCells() {
    	return null; // TODO
    }
    
}





/**
 * Helps to create and organize commands for each turn.
 */
class CommandManager {
	
	private static long time;
    
    static {
    	time = System.currentTimeMillis();
    }

    /**
     * Flushes the commands and executes them for the current round. Ends the round.
     */
    static void executeCommands() {
    	StringBuilder sb = new StringBuilder();
        
        for (Cell cell : GameState.friendlyCells) {
            sb.append(cell.commands.get(0).getCommandString());
            RoundCommands roundCommands = cell.commands.remove(0);
            roundCommands.reset();
            cell.commands.add(roundCommands);
        }
        
    	sb.append("MSG " + (System.currentTimeMillis()-time) + "ms - " + GameState.ownTotalUnits + "/" + GameState.enemyTotalUnits + " - " + GameState.ownTotalProduction + "/" + GameState.enemyTotalProduction + ";");
        time = System.currentTimeMillis();
        
        System.out.println(sb.toString());
    }


	/**
     * Sends units from one cell to another.
     * @param from The cell ID from where the units are send.
     * @param to The cell ID to where the units are send
     * @param units The amount of units send.
     * @param priority The priority of this command. The higher, the more important.
     * @param inRounds The delay in rounds until this command is executed.
     */
    static void standardAttack(int from, int to, int units, int inRounds) {
    	GameState.getCellByID(from).commands.get(inRounds).standardAttack = new SendUnits(from, to, units);
    }
    
    /**
     * Sends units from one cell to another.
     * @param from The cell ID from where the units are send.
     * @param to The cell ID to where the units are send
     * @param units The amount of units send.
     * @param priority The priority of this command. The higher, the more important. 
     */
    static void standardAttack(int from, int to, int units) {
    	standardAttack(from, to, units, 0);
    }
    
    /**
     * Saves units to help defend the cell.
     * @param cellID The cell ID where the units should be saved.
     * @param units The amount of units saved.
     */
    static void saveUnitsForDefense(int cellID, int units) {
    	GameState.getCellByID(cellID).commands.get(0).saveUnitsForDefense = new SaveUnits(cellID, units);
    }
    
    static void sendUnitsForDefense(int from, int to, int units) {
    	GameState.getCellByID(from).commands.get(0).defendCellBySendingUnits = new SendUnits(from, to, units);
    }
    
    /**
     * Sends units from one cell to another.
     * @param from The cell ID from where the units are send.
     * @param to The cell ID to where the units are send
     * @param units The amount of units send.
     * @param priority The priority of this command. The higher, the more important.
     * @param inRounds The delay in rounds until this command is executed.
     */
    static void specialAttack(int from, int to, int units, int inRounds) {
    	GameState.getCellByID(from).commands.get(inRounds).specialAttack = new SendUnits(from, to, units);
    }
    
    /**
     * Sends units from one cell to another.
     * @param from The cell ID from where the units are send.
     * @param to The cell ID to where the units are send
     * @param units The amount of units send.
     * @param priority The priority of this command. The higher, the more important. 
     */
    static void specialAttack(int from, int to, int units) {
    	specialAttack(from, to, units, 0);
    }
    
    /**
     * Save units for a special attack.
     * @param cellID The cell ID where units should be saved
     * @param units The amount of units saved.
     */
    public static void saveUnitsForSpecialAttack(int cellID, int units) {
    	GameState.getCellByID(cellID).commands.get(0).saveUnitsForSpecialAttack = new SaveUnits(cellID, units);
	}
    
    /**
     * Sends units from one cell to another.
     * @param from The cell ID from where the units are send.
     * @param to The cell ID to where the units are send
     * @param units The amount of units send.
     * @param priority The priority of this command. The higher, the more important.
     * @param inRounds The delay in rounds until this command is executed.
     */
    static void neutralAttack(int from, int to, int units, int inRounds) {
    	GameState.getCellByID(from).commands.get(inRounds).neutralAttacks.add(new SendUnits(from, to, units));
    }
    
    /**
     * Sends units from one cell to another.
     * @param from The cell ID from where the units are send.
     * @param to The cell ID to where the units are send
     * @param units The amount of units send.
     * @param priority The priority of this command. The higher, the more important. 
     */
    static void neutralAttack(int from, int to, int units) {
    	neutralAttack(from, to, units, 0);
    }
    
    /**
     * Sends a bomb from one cell to another.
     * @param from The cell ID from where the bomb is send.
     * @param to The cell ID to where the bomb is send.
     * @param priority The priority of this command. The higher, the more important.
     * @param inRounds The delay in rounds until this command is executed.
     */
    static void sendBomb(int from, int to, int inRounds) {
    	GameState.getCellByID(from).commands.get(inRounds).bombAttacks.add(new SendBomb(from, to));
    }

    /**
     * Sends a bomb from one cell to another.
     * @param from The cell ID from where the bomb is send.
     * @param to The cell ID to where the bomb is send.
     * @param priority The priority of this command. The higher, the more important. 
     */
    static void sendBomb(int from, int to) {
    	sendBomb(from, to, 0);
    }

    /**
     * Upgrade the production of a cell.
     * @param cellID The id of the cell to upgrade the productivity.
     * @param inRounds The delay in rounds until the command is executed.
     */
    static void increaseProductivity(int cellID, int inRounds) {
    	GameState.getCellByID(cellID).commands.get(inRounds).upgradeCell = new UpgradeCell(cellID);
    }
    
    /**
     * Upgrade the production of a cell.
     * @param cellID The id of the cell to upgrade the productivity.
     */
    static void increaseProductivity(int cellID) {
    	increaseProductivity(cellID, 0);
    }
    
    /**
     * Makes sure that all units are evacuate.
     * @param cellID The id of the cell to evacuate.
     */
    static void evacuateUnits(int cellID) {
    	GameState.getCellByID(cellID).commands.get(0).evacuateUnits = true;
    }
    
    /**
     * Prints a debug message in the console.
     * @param message The debug message.
     */
    static void log(String message) {
        System.err.println(message);
    }
}

class RoundCommands {
	
	int cellID;
	
	SendUnits standardAttack;
	List<SendUnits> neutralAttacks;
	UpgradeCell upgradeCell;
	SendUnits defendCellBySendingUnits;
	SaveUnits saveUnitsForDefense;
	SendUnits specialAttack;
	SaveUnits saveUnitsForSpecialAttack;
	ArrayList<SendBomb> bombAttacks;
	
	boolean evacuateUnits;
	
	public RoundCommands(int cellID) {
		this.cellID = cellID;
		reset();
	}
	
	public void reset() {
		standardAttack = null;
		neutralAttacks = new ArrayList<SendUnits>();
		upgradeCell = null;
		defendCellBySendingUnits = null;
		saveUnitsForDefense = null;
		specialAttack = null;
		saveUnitsForSpecialAttack = null;
		bombAttacks = new ArrayList<SendBomb>();
		
		evacuateUnits = false;
	}

	public String getCommandString() {
		StringBuilder sb = new StringBuilder();
		
		int remainingUnits = GameState.getCellByID(cellID).units;
		
		for (SendBomb sendBomb : bombAttacks) {
			sb.append(sendBomb.getCommandString(remainingUnits));
		}
		
		
		if (remainingUnits <= 0) { return sb.toString(); }
		
		if (specialAttack != null) {
			sb.append(specialAttack.getCommandString(remainingUnits));
			remainingUnits -= specialAttack.units;
			if (remainingUnits <= 0) { return sb.toString(); }
		}

		if (saveUnitsForSpecialAttack != null && !evacuateUnits) {
			sb.append(saveUnitsForSpecialAttack.getCommandString(remainingUnits));
			remainingUnits -= saveUnitsForSpecialAttack.units;
			if (remainingUnits <= 0) { return sb.toString(); }
		}
		
		if (defendCellBySendingUnits != null) {
			sb.append(defendCellBySendingUnits.getCommandString(remainingUnits));
			remainingUnits -= defendCellBySendingUnits.units;
			if (remainingUnits <= 0) { return sb.toString(); }
		}
		
		if (saveUnitsForDefense != null && !evacuateUnits) {
			sb.append(saveUnitsForDefense.getCommandString(remainingUnits));
			remainingUnits -= saveUnitsForDefense.units;
			if (remainingUnits <= 0) { return sb.toString(); }
		}
		
		if (upgradeCell != null && !evacuateUnits) {
			if (remainingUnits < 10) {
				return sb.toString();
			}
			sb.append(upgradeCell.getCommandString(remainingUnits));
			remainingUnits -= 10;
			if (remainingUnits <= 0) { return sb.toString(); }
		}
		
		if (neutralAttacks.size() > 0) {
			for (SendUnits sendUnits : neutralAttacks) {
				sb.append(sendUnits.getCommandString(remainingUnits));
				remainingUnits -= sendUnits.units;
				if (remainingUnits <= 0) { return sb.toString(); }
			}
		}
		
		if (standardAttack != null) {
			sb.append(standardAttack.getCommandString(remainingUnits));
			remainingUnits -= standardAttack.units;
		}
		
		if (evacuateUnits && remainingUnits > 0) {
			if (standardAttack != null) {
				sb.append("MOVE " + standardAttack.from + " " + standardAttack.to + " " + remainingUnits + ";");
			} else {
				sb.append("MOVE " + cellID + " " + GameState.getCellByID(cellID).findSaveCellToEvacuateUnitsTo().id + " " + remainingUnits + ";");
			}
		}
		
		return sb.toString();
	}
}



/**
 * Provides all available information about all troops and bombs launched over the course of the current game.
 */
class History {
	
	public HashSet<Integer> troopIDs; // list of all troop ids of all launched troops
	public ArrayList<ArrayList<Troop>> troopsLaunched; // list of launched troops according to the round they were launched in.
	
	public HashSet<Integer> bombIDs; // list of all bomb ids of all launched bombs
	public ArrayList<Bomb> bombs; // list of launched bombs
    
    public History() {
    	troopIDs = new HashSet<Integer>();
        troopsLaunched = new ArrayList<ArrayList<Troop>>(201);
        for (int i = 0; i < 201; i++) {
            troopsLaunched.add(new ArrayList<Troop>());
        }
        
        bombIDs = new HashSet<Integer>();
        bombs = new ArrayList<Bomb>(4);
    }
    
}

class Neighbor implements Comparable<Neighbor> { // also used as Vertex for Dijkstra algorithm
	
	int cellID;
	int distance;
	
	public Neighbor(int cellID, int distance) {
		this.cellID = cellID;
		this.distance = distance;
	}

	@Override
	public int compareTo(Neighbor other) {
		if (this.distance > other.distance)
			return 1;
		else if (this.distance < other.distance)
			return -1;
		else
			return 0;
	}
	
	public Cell getCell() {
		return GameState.getCellByID(cellID);
	}
	
}

class PredictedBombImpact {
	
	Bomb bomb; // the bomb in question which forces special limitations on this cell
	int predictedImpact; // the round in which the bomb might arrive at this cell
	
	public PredictedBombImpact(Bomb bomb, int predictedImpact) {
		this.bomb = bomb;
		this.predictedImpact = predictedImpact;
	}
}

class Troop {

    int id;
    int owner;
    int from;
    int to;
    int units;
    int launched; // the round in which the troop was launched
    int arrival; // the round in which the troop will arrive at the destination cell

    public Troop(int id, int owner, int from, int to, int units, int launched, int arrival) {
        this.id = id;
        this.owner = owner;
        this.from = from;
        this.to = to;
        this.units = units;
        this.launched = launched;
        this.arrival = arrival; 
    }

}

class Bomb {

    int id;
    int owner;
    int from;
    int to; // (-1 if enemy bomb)
    int launched; // the round the bomb was launched
    int impact; // the round the bomb will arrive at the destination cell and detonate (-1 if enemy bomb)

    public Bomb(int id, int owner, int from, int to, int launched, int impact) {
        this.id = id;
        this.owner = owner;
        this.from = from;
    	this.launched = launched;
        if (owner == 1) {
        	this.to = to;
        	this.impact = impact;
        } else {
        	this.to = -1; // unknown;
        	this.impact = -1; // unknown;
        }
    }

}


abstract class Command {
	
	public abstract String getCommandString(int remainingUnits);
	
}

class SendUnits extends Command {
	
	public int from;
	public int to;
	public int units;
	
	public SendUnits(int from, int to, int units) {
		this.from = from;
		this.to = to;
		this.units = units;
	}
	
	@Override
	public String getCommandString(int remainingUnits) {
		if (remainingUnits >= units) {
			return "MOVE " + from + " " + to + " " + units + ";";
		} else {
			return "MOVE " + from + " " + to + " " + remainingUnits + ";";
		}
	}
}

class SaveUnits extends Command {
	
	public int cellID;
	public int units;
	
	public SaveUnits(int cellID, int units) {
		this.cellID = cellID;
		this.units = units;
	}
	
	@Override
	public String getCommandString(int remainingUnits) {
		return "";
	}
}

class SendBomb extends Command {
	
	public int from;
	public int to;
	
	public SendBomb(int from, int to) {
		this.from = from;
		this.to = to;
	}
	
	@Override
	public String getCommandString(int availableUnits) {
		return "BOMB " + from + " " + to + ";";
	}
}

class UpgradeCell extends Command {
	
	public int cellID;
	
	public UpgradeCell(int cellID) {
		this.cellID = cellID;
	}
	
	@Override
	public String getCommandString(int remainingUnits) {
		return "INC " + cellID + ";";
	}
}



enum ThreatStatus {
	SAFE,
	DEFEND_BY_SAVING_UNITS,
	DEFEND_BY_INCOMING_UNITS,
	BEING_CONQUERED
}
