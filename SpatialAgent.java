package familiarity;

import sim.engine.SimState;
import sim.field.continuous.Continuous2D;
import sim.field.grid.Grid2D;
import sim.field.grid.SparseGrid2D;
import sim.util.Bag;
import sim.util.Double2D;

public class SpatialAgent extends Agent {

	// store last direction moved
	double[] movedir;
	
	public SpatialAgent(familiarModel model, double[] gene, double initfitness, double x, double y) {
		super(model, gene, initfitness, x, y);
		// initialize direction of movement as random
		if(model.cont) {
			// chose a random angle
			double angle = Math.toRadians(model.random.nextDouble()*360);
			// move in that direction (using sine and cosine)
			this.movedir = new double[] {Math.cos(angle), Math.sin(angle)};
		} else {
			this.movedir = new double[] {model.random.nextInt(3)-1, model.random.nextInt(3)-1};
		}
	}

	public void step(SimState state) {
		// cast state as model
		familiarModel model = (familiarModel) state;
		// move continuously or discretely
		if(model.cont) {
			contMove(model);
		} else {
			discMove(model);
		}
		// this has to be at the end so agents don't move after they die
		super.step(state);
	}

	/*
	 * Discrete movement (loose implementation of active movement in Joshi et al. 2017)
	 * To be phased out
	 */
	public void discMove(familiarModel model) {
		// cast the space as a grid
		SparseGrid2D grid = (SparseGrid2D) model.agentNet;
		// if this agent is making an error, just move randomly
		if(model.random.nextBoolean(model.error)) {
			double[][] opts = new double[][] {{1,1},{1,0},{0,1},{-1,-1},{-1,0},{0,-1},{1,-1},{-1,1}};
			int choice = model.random.nextInt(opts.length);
			this.movedir = opts[choice];
		} else {
			// otherwise check surroundings for agents and move accordingly
			Bag neighbors = grid.getMooreNeighbors((int) this.netx, (int) this.nety, (int) model.viewrange, Grid2D.TOROIDAL, false);
			// if the bag isn't empty calculate direction of movement based on nearby agents
			if(!neighbors.isEmpty() && model.random.nextDouble() > model.error) {
				// get weight in favor of moving in each direction on each axis
				double[] pos = {0,0};
				double[] neg = {0,0};
				double[] zero = {0,0};
				// grab the nearest agent to play the prisoners' dilemma with
				SpatialAgent nearest = null;
				Double neardist = null;
				// loop through the neighbors
				for(int i = 0; i < neighbors.numObjs; i++) {
					// grab this agent
					SpatialAgent a = (SpatialAgent) neighbors.get(i);
					// calculate the distance on each axis separately
					double distx = a.netx-this.netx;
					double disty = a.nety-this.nety;
					// if either of them is outside the range of vision, adjust by 100
					double[] cordists = adjustLoc(new double[] {distx, disty}, new double[] {model.viewrange, -model.viewrange});
					// then calculate the actual distance
					double dist = Math.sqrt(Math.pow(cordists[0], 2)+Math.pow(cordists[1], 2));
					// if this is closer than the nearest, make this the nearest
					if(neardist == null || dist < neardist) {
						nearest = a;
						neardist = dist;
					}
					double bias;
					// use whether it's familiar to set the bias
					if(model.famNet.getEdge(this, a) != null) {
						bias = this.famBias;
					} else {
						bias = 1-this.famBias;
					}
					// grab the suggested direction of movement on each axis based on that agent for both aggregation and flocking
					int[][] dirs = new int[2][2];
					dirs[1][0] = (int) a.movedir[0];
					dirs[1][1] = (int) a.movedir[1];
					dirs[0][0] = (int) cordists[0];
					dirs[0][1] = (int) cordists[1];
					// and the weights for flocking versus aggregating
					double[] weight = {1-model.flockstr-model.contstr, model.flockstr};
					// now add to the relevant totals
					for(int k = 0; k < 2; k++) {
						for(int j = 0; j < 2; j++) {
							// check which direction to go in based on that agent and add to the appropriate total
							if(dirs[k][j] > 0) {
								pos[j] += bias*weight[k];
							} else if(dirs[k][j] < 0) {
								neg[j] += bias*weight[k];
							} else {
								zero[j] += bias*weight[k];
							}
						}
					}
				}
				// set move direction for each axis based on which was most strongly weighted
				// TODO - make sure this isn't secretly biased
				for(int i = 0; i < 2; i++) {
					// add this agent's direction to the relevant total, with its own weight
					if(this.movedir[i] > 0) {
						pos[i] += model.contstr;
					} else if(this.movedir[i] < 0) {
						neg[i] += model.contstr;
					} else {
						zero[i] += model.contstr;
					}
					// if zero is greater than positive and negative, set the direction to zero
					if(zero[i] > pos[i] && zero[i] > neg[i]) {
						this.movedir[i] = 0;
					} else if(pos[i] > neg[i]) {
						// otherwise, if positive is greater, make it one
						this.movedir[i] = 1;
					} else if(neg[i] > pos[i]) {
						// otherwise, if negative is greater, make it negative one
						this.movedir[i] = -1;
					} else {
						// otherwise, choose randomly between 1 and -1
						this.movedir[i] = (2*model.random.nextInt(2))-1;
					}
				}
				// if the agents should always be in motion and this agent isn't moving, move randomly instead
				if(model.alwaysmove && this.movedir[0] == 0 && this.movedir[1] == 0) {
					double[][] opts = new double[][] {{1,1},{1,0},{0,1},{-1,-1},{-1,0},{0,-1},{1,-1},{-1,1}};
					int choice = model.random.nextInt(opts.length);
					this.movedir = opts[choice];
				}
				// make sure neither this agent or the nearest has interacted yet this turn
				if(this.interact < model.schedule.getSteps() && nearest.interact < model.schedule.getSteps()) {
					// have them play a game
					this.playGame(model, nearest);
					// increment the number of times they've interacted
					this.interact++;
					nearest.interact++;
				}
			}
		}
		// calculate the new locations (accounting for toroidal movement)
		int newx = grid.stx((int) (this.netx+this.movedir[0]));
		int newy = grid.sty((int) (this.nety+this.movedir[1]));
		// check to see if there are other agents in the new location
		Bag newloc = grid.getObjectsAtLocation(newx, newy);
		// if there is, try to move randomly instead (if that switch is on)
		// TODO - possibly replace with explicitly choosing an empty location (from Jeff's code)
		// TODO - also possibly make this not change the agent's apparent direction of motion
		if(model.randbounce && newloc != null) {
			double[][] opts = new double[][] {{1,1},{1,0},{0,1},{-1,-1},{-1,0},{0,-1},{1,-1},{-1,1}};
			int choice = model.random.nextInt(opts.length);
			this.movedir = opts[choice];
			newx = grid.stx((int) (this.netx+this.movedir[0]));
			newy = grid.sty((int) (this.nety+this.movedir[1]));
			newloc = grid.getObjectsAtLocation(newx, newy);
		}
		// if this agent managed to find a free space, actually move forward
		if(newloc == null) {
			// update location
			this.netx = newx;
			this.nety = newy;
			grid.setObjectLocation(this, (int) this.netx, (int) this.nety);
		}
	}

	/*
	 * Continuous movement (more literal implementation of active movement in Joshi et al. 2017)
	 */
	public void contMove(familiarModel model) {
		// cast the space as continuous
		Continuous2D space = (Continuous2D) model.agentNet;
		// store this agent's location as a 2D double
		Double2D loc = new Double2D(this.netx, this.nety);
		// start a counter for direction of movement in each direction
		double[] dir = {0, 0};			
		// and grab the nearest neighbor to interact with
		SpatialAgent nearest = null;
		double neardist = 0;
		// first, check for any agents within the repulsion distance
		Bag repulse = space.getNeighborsExactlyWithinDistance(loc, model.repulserange, true);
		// if any agents are too close, move away from them (I think this automatically includes this agent)
		if(repulse.numObjs > 1) {
			// loop through all the agents
			for(Object o : repulse) {
				// make sure it isn't this one
				if(o != this) {
					// cast it as an agent
					SpatialAgent a = (SpatialAgent) o;
					// grab the distance between the two agents
					double dist = space.tds(new Double2D(a.netx, a.nety), loc);
					// add the direction to move away from the other agent to the count for each direction, normalized by distance
					dir[1] += space.tdy(this.nety, a.nety)/dist;
					dir[0] += space.tdx(this.netx, a.netx)/dist;
					// if nearest is null, or it's closer than nearest, make this one nearest
					if(nearest == null || dist < neardist) {
						nearest = a;
						neardist = dist;
					}
				}
			}
			// normalize the total
			dir = normArray(dir);
		} else {
			// otherwise, check for neighbors a little farther away to flock/aggregate with
			Bag approach = space.getNeighborsExactlyWithinDistance(loc, this.viewrange, true);
			// if it isn't empty, flock/aggregate
			if(approach.numObjs > 1) {
				// sum up separately for flocking and aggregating, and bring them together later
				double[] flockdir = {0, 0};
				double[] aggdir = {0, 0};
				// loop through all the neighboring agents
				for(Object o : approach) {
					// make sure it isn't this one
					if(o != this) {
						// cast the object as an agent
						SpatialAgent a = (SpatialAgent) o;
						// grab the relevant bias based on familiarity
						double bias;
						if(model.famNet.getEdge(this, a) != null) {
							bias = this.famBias;
						} else {
							bias = 1-this.famBias;
						}
						// get the distance to the agent
						double dist = space.tds(new Double2D(a.netx, a.nety), loc);
						// add in the direction to go toward the agent to aggregate, normalized by distance
						aggdir[0] += bias*space.tdx(a.netx, this.netx)/dist;
						aggdir[1] += bias*space.tdy(a.nety, this.nety)/dist;
						// and the direction the agent is moving to flock (also normalized?)
						flockdir[0] += bias*a.movedir[0];
						flockdir[1] += bias*a.movedir[1];
						// if nearest is null, or it's closer than nearest, make this one nearest
						if(nearest == null || dist < neardist) {
							nearest = a;
							neardist = dist;
						}
					}
				}
				// I'm going to try normalizing both flocking and aggregating, so they're both one unit of movement
				aggdir = normArray(aggdir);
				flockdir = normArray(flockdir);
				// calculate the overall direction of movement on each axis, based on strength of flocking, aggregation, and current direction, with some error
				for(int i = 0; i < 2; i++) {
					dir[i] = (1-model.contstr)*(1-model.flockstr)*aggdir[i] + (1-model.contstr)*model.flockstr*flockdir[i] 
							+ model.contstr*this.movedir[i];
				}
			} else {
				// if there aren't any other agents within view, just keep moving in the same direction with some error
				dir[0] = model.contstr*this.movedir[0];
				dir[1] = model.contstr*this.movedir[1];
			}
		}
		// no matter what, add in some error
		dir[0] += model.random.nextGaussian()*model.error;
		dir[1] += model.random.nextGaussian()*model.error;
		
		// choose which agent to interact with (either the nearest, or chosen randomly within the interact range)
		SpatialAgent partner = nearest;
		// if they're supposed to choose randomly, do so
		if(model.randinteract) {
			// grab all the agents within the range
			Bag neighbors = space.getNeighborsExactlyWithinDistance(loc, model.interactrange, true);
			if(neighbors.numObjs > 1) {
				// if there are other agents in the bag, make a random draw
				int draw = model.random.nextInt(neighbors.numObjs);
				// get that agent out of the bag
				Object o = neighbors.get(draw);
				// if it's this one, draw again
				if(o == this) {
					int d = model.random.nextInt(neighbors.numObjs-1);
					// if d is less than the index of this agent, then it's fine, if it's greater than or equal, add one
					if(d < draw) {
						o = neighbors.get(d);
					} else {
						o = neighbors.get(d+1);
					}
				}
				// whatever o ends up  being, make that the partner
				partner = (SpatialAgent) o;
			}else {
				// if there aren't any agents in the bag (or just this one), then return null
				partner = null;
			}
		}
		// interact with the nearest neighbor if there is one and neither agent has interacted yet this turn (and they don't all play at the end)
		if(!model.oneshot && partner != null && this.interact < model.schedule.getSteps() && partner.interact < model.schedule.getSteps()) {
			// have them play a game
			this.playGame(model, partner);
			// increment the number of times they've interacted
			this.interact++;
			partner.interact++;
		}
		
		// now for actually moving
		// get the desired angle of movement
		double newangle = Math.atan2(dir[1], dir[0]);
		// and the original angle of movement
		double oldangle = Math.atan2(this.movedir[1], this.movedir[0]);
		// the maximum angle is determined by the step size
		double maxangle = model.stepsize*Math.toRadians(model.maxrotate);
		// make sure the angle of change isn't greater than the maximum rotation
		if(Math.abs(newangle - oldangle) > maxangle && Math.abs(2*Math.PI - Math.abs(newangle-oldangle)) > maxangle) {
			if(Math.abs(2*Math.PI - Math.abs(newangle-oldangle)) < Math.abs(newangle-oldangle)) {
				newangle = oldangle - maxangle*Math.signum(newangle-oldangle);
			} else {
				newangle = oldangle + maxangle*Math.signum(newangle-oldangle);
			}
		} 
		// take the new angle of movement, whatever it may be, and use sine and cosine to actually translate it into movement in each direction
		this.movedir[1] = Math.sin(newangle);
		this.movedir[0] = Math.cos(newangle);
		
		// since I don't do anything with the angle of movement, I'm just going to normalize to get movement on a unit circle
//		double magnitude = Math.sqrt(dir[0]*dir[0] + dir[1]*dir[1]);
//		this.movedir[0] = model.stepsize*dir[0]/magnitude;
//		this.movedir[1] = model.stepsize*dir[1]/magnitude;
		
		// then add it to the agent's current location (adjusted for toroidal movement)
		this.netx = space.stx(this.netx+model.stepsize*this.movedir[0]);
		this.nety = space.sty(this.nety+model.stepsize*this.movedir[1]);
		space.setObjectLocation(this, new Double2D(this.netx, this.nety));
	}
	
	/*
	 * Helper function to normalize distances, mostly to test something out
	 */
	public static double normDist(double dist) {
		if(dist == 0) {
			return dist;
		} else {
			return dist/Math.abs(dist);
		}
	}
	
	/*
	 * Another helper function to normalize arrays (just of two entries for now)
	 */
	public static double[] normArray(double[] array) {
		// grab the magnitude of the array
		double mag = Math.sqrt(array[0]*array[0] + array[1]*array[1]);
		if(mag == 0) {
			return array;
		} else {
			array[0] /= mag;
			array[1] /= mag;
			return array;
		}
	}

	/*
	 * Adjusts location for toroidal movement
	 * TODO - phase this out in favor of stx and sty
	 */
	public static double[] adjustLoc(double[] loc, double[] range) {
		if(loc[0] > range[0]) {
			loc[0] -= 100;
		} else if(loc[0] < range[1]) {
			loc[0] += 100;
		}
		if(loc[1] > range[0]) {
			loc[1] -= 100;
		} else if(loc[1] < range[1]) {
			loc[1] += 100;
		}
		return(loc);
	}

}
