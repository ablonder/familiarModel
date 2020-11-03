package familiarity;

import java.awt.Color;
import java.awt.Graphics2D;

import javax.swing.JFrame;

import sim.display.Console;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.field.SparseField2D;
import sim.portrayal.DrawInfo2D;
import sim.portrayal.FieldPortrayal2D;
import sim.portrayal.continuous.ContinuousPortrayal2D;
import sim.portrayal.grid.SparseGridPortrayal2D;
import sim.portrayal.network.NetworkPortrayal2D;
import sim.portrayal.network.SimpleEdgePortrayal2D;
import sim.portrayal.network.SpatialNetwork2D;
import sim.portrayal.simple.CircledPortrayal2D;
import sim.portrayal.simple.OrientedPortrayal2D;
import sim.portrayal.simple.OvalPortrayal2D;

public class famModelUI extends GUIState {

	public Display2D display;
	public JFrame frame;
	public NetworkPortrayal2D famNetPortrayal = new NetworkPortrayal2D();
	public FieldPortrayal2D agentPortrayal;
	
	public famModelUI(SimState state) {
		super(state);
		// cast the state as a model to look at it
		familiarModel model = (familiarModel) state;
		// if the model is continuous make a continuous portrayal
		if(model.cont) {
			agentPortrayal = new ContinuousPortrayal2D();
		} else {
			// otherwise make a grid portrayal
			agentPortrayal = new SparseGridPortrayal2D();
		}
	}
	
	public String setName() {
		return("Familiarity Model");
	}
	
	public void init(Controller c) {
		super.init(c);
		display = new Display2D(600, 600, this);
		display.setClipping(false);
		
		frame = display.createFrame();
		frame.setTitle("Familiarity Display");
		c.registerFrame(frame);
		frame.setVisible(true);
		display.attach(agentPortrayal, "Agents");
		display.attach(famNetPortrayal, "Network");
	}
	
	public void start() {
		super.start();
		setupPortrayals();
	}
	
	public void load(SimState state) {
		this.state = state;
	}
	
	public void setupPortrayals() {
		// cast the state as a model
		familiarModel model = (familiarModel) state;
		// set up the grid
		agentPortrayal.setField(model.agentNet);
		// draw each agent
		agentPortrayal.setPortrayalForAll(new CircledPortrayal2D(new OvalPortrayal2D(){
			public void draw(Object object, Graphics2D graphics, DrawInfo2D info) {
			// cast the object as an agent
			Agent agent = (Agent) object;
			// grab the agent's color
			paint = agent.color;
			// draw it
			super.draw(object, graphics, info);
			}}, 0, 1, Color.gray, false));
		// TODO - get the outline to be blue or red based on whether the agent is a cooperator or defector
		// set up the network if it's going to be displayed as well
		if(model.displayNet) {
			famNetPortrayal.setField(new SpatialNetwork2D((SparseField2D) model.agentNet, model.famNet));
			famNetPortrayal.setPortrayalForAll(new SimpleEdgePortrayal2D(new Color(Color.gray.getRed(), Color.gray.getGreen(), Color.gray.getBlue(), 100), null));
		}
		// reschedule displayer
		display.reset();
		display.setBackdrop(Color.white);
		// redraw display
		display.repaint();
	}
	
	public void quit() {
		super.quit();
		if(frame != null) frame.dispose();
		frame = null;
		display = null;
	}
	
	public static void main(String[] args) {
		// this needs to create a model, make sure gui is set to true or it'll just run normally
		familiarModel model = new familiarModel("GUIInput.txt");
		famModelUI vid = new famModelUI(model);
		Console c = new Console(vid);
		c.setVisible(true);
	}
}
