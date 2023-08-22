import java.awt.Dimension;
import java.awt.Toolkit;

import javax.swing.JFrame;

/*
Charge Simulator v.1.0
Jason Kim
Version History:
v.4.0
 - Magnetic Fields
v.3.0
 - Settings Menu
    - Adjustable key bindings
    - GUI scale
 - F1 - Picture Mode
 - F2 - Screenshot
 - New Visualization: 4 - Contour (Equipotential Lines)
 - Can now place Sensors
    - Display field information at selected location
v.2.0
 - Color Visualization v.2
    - Better coloring method
 - Click and drag to place charges with velocity
 - Click and drag charges to move them
    - Not following another charge
       - Drag and move around
    - Following another charge
       - The held charge will follow you which follows the charge you follow
 - Adjust velocities
    - Click and drag velocity vector of followed charge
v.1.1
 - Arrows are now actually arrows
    - Lines also have arrows
 - Can now select scenarios
 - Zoom with scroll wheel
v.1.0
 - New visualization order:
    - 1 - Color
    - 2 - Arrows
    - 3 - Lines
 - Mouse Inputs
 	- Add charges with left-click
 	- Delete charges with right-click
 	- Select charge to focus camera
 	   - Can follow it around when unpaused
 - Other
    - Regeneration now only occurs within visible screen
v.0.3
 - Camera Translation
 	- WASD
 - Color Visualization v.1
    - Basic coloring (black and white)
    - Relative coloring (seizure warning)
 - Toggle charge view
v.0.2
 - Electric Field Lines (seizure warning)
    - simple integration method
    - can modify steps and step length
v.0.1
 - Construction of basic engine
    - Time
       - Time manipulation
    - FPS
    - Zoom in and out
    - Basic GUI
       - Debug Menu (F3)
 - Charges
    - can impart a force on other charges
    - Electric Field
       - affected by charges
       - has different visualizations
          - Arrows
          - Lines (not yet implemented)
          - Color (not yet implemented)
 */

public class ChargeSimDriver {
	public static void main(String[] args) {
		JFrame frame = new JFrame("Electric Fields");
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize(); // gets resolution
		ChargeSimPanel panel = new ChargeSimPanel(screenSize.getWidth(), screenSize.getHeight()); // inputs resolution
		frame.add(panel); // panel goes in frame
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setExtendedState(JFrame.MAXIMIZED_BOTH); // window size
		frame.setUndecorated(true); // window bar
		frame.setVisible(true); // window visible
	}
}
