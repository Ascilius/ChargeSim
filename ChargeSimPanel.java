import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.swing.JPanel;

/*
Charge Simulator v.1.0
Jason Kim
dont steal plz
*/

public class ChargeSimPanel extends JPanel implements MouseWheelListener {

	// debug
	private boolean debug = false;

	// screen
	private int screenWidth, screenHeight;
	private double screenScale = 1.0;
	private boolean invert = false;
	private boolean help = false;

	// frames
	private long frameTime = 0; // milliseconds
	private int targetFPS = 60;
	private double targetTime = 1000.0 / targetFPS; // milliseconds
	private boolean firstFrame = true;
	private int totalFrames = 0;
	private int totalTime = 0; // ms (need to convert to s)
	private double currentFPS = 0;
	private boolean paused = true;
	private double timeScale = 1.0;

	// camera
	private double cameraX = 0.0;
	private double cameraY = 0.0;
	private double cameraSpeed = 1000; // pixels/s
	private int[] cameraDirection = { 0, 0 }; // direction vector (needs to be converted to unit vector)
	private ArrayList<Integer> heldKeys = new ArrayList<Integer>(); // array of held keys

	// inputs
	private KeyHandler keyHandler; // key inputs
	private MouseHandler mouseHandler; // mouse inputs
	private Point click = new Point(0, 0); // last mouse click
	private boolean placing = false; // currently placing charge
	private Charge newCharge = null; // charge to be placed
	private double[] press = new double[2]; // last mouse press
	private double[] location = new double[2]; // mouse location
	private double[] release = new double[2]; // last mouse release
	private final double chargeSpeed = 0.5; // speed multiplier from pixel to m/s

	// charges
	private ArrayList<Charge> charges = new ArrayList<Charge>();
	private int genType = 1; // 0 - nothing, 1 - random, 2 - same, 3 - opposite
	private final int numCharges = 100; // number of charges to generate
	private final int radius = 5; // apparent radius of the charges
	private boolean chargeView = true; // see charges
	private Charge selectedCharge = null; // charge that has been selected to follow

	// electric field
	private double k = 9.0 * Math.pow(10, 9);
	private int visualType = 0; // electric field visualization: 0 - none, 1 - color, 2 - arrows, 3 - lines

	// color
	private int width = 8; // width/length of color square
	private double max = 0; // max electric field; used to calculate color
	private double exposure = 8; // brightness(?)
	private boolean magnitude = true; // red/blue coloring
	private boolean relative = false; // relative coloring

	// arrows
	private double precision = 10; // meters (space between arrows)
	private double preclenRatio = 4;
	private double arrowLength = precision / preclenRatio; // length of the arrow (m)

	// lines
	private int numLines = 16; // number of lines
	private int buffer = 5; // buffer around charge
	private int steps = 5000; // num of points in each line
	private double stepLength = 0.5; // step of the lines
	private boolean optimize = false; // disables line generation for negative charges

	public ChargeSimPanel(double screenWidth, double screenHeight) {
		// screen
		this.screenWidth = (int) screenWidth;
		this.screenHeight = (int) screenHeight;

		// inputs
		this.keyHandler = new KeyHandler();
		addKeyListener(this.keyHandler);
		this.mouseHandler = new MouseHandler();
		addMouseListener(this.mouseHandler);
		addMouseWheelListener(this);
		setFocusable(true);

		// charges
		genCharges();
	}

	// generates charges
	public void genCharges() {
		// reseting simulation
		cameraX = 0;
		cameraY = 0;
		screenScale = 1.0;
		timeScale = 1.0;
		selectedCharge = null;
		paused = true;
		// generating new charges
		charges.clear();
		firstFrame = true;
		if (genType == 1) {
			for (int i = 0; i < numCharges; i++) {
				// charge and mass
				double charge = 1.60217663 * Math.pow(10, -19);
				double mass = 1.67262192 * Math.pow(10, -27);
				if (Math.random() > 0.5) {
					charge *= -1;
					mass = 9.1093837 * Math.pow(10, -31);
				}
				// location
				double sx = (screenWidth / -2 + Math.random() * screenWidth) / screenScale;
				double sy = (screenHeight / -2 + Math.random() * screenHeight) / screenScale;
				// initial velocity (to prevent "collisions")
				double v = 5 + Math.random() * 5;
				double theta = Math.random() * 2 * Math.PI;
				double vx = v * Math.cos(theta);
				double vy = v * Math.sin(theta);
				// new charge
				charges.add(new Charge(charge, mass, sx, sy, vx, vy, false));
			}
		} else if (genType == 2) {
			charges.add(new Charge(1 * Math.pow(10, -2), 1, -100, 0, 0, 0, false));
			charges.add(new Charge(1 * Math.pow(10, -2), 1, 100, 0, 0, 0, false));
		} else if (genType == 3) {
			charges.add(new Charge(-1 * Math.pow(10, -2), 1, -100, 0, 0, 0, false));
			charges.add(new Charge(1 * Math.pow(10, -2), 1, 100, 0, 0, 0, false));
		}
	}

	// painting
	public void paintComponent(Graphics graphics) {
		Graphics2D g = (Graphics2D) graphics;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// frame start
		final long startTime = System.currentTimeMillis();

		// background
		g.setColor(Color.BLACK);
		if (invert == true)
			g.setColor(Color.WHITE);
		g.fillRect(0, 0, screenWidth, screenHeight);

		// sim stuff
		g.translate(screenWidth / 2, screenHeight / 2);

		// electric field
		if (firstFrame || visualType == 1) { // color
			// bounds
			if (firstFrame || relative) // initial max calculation
				max = 0;
			// calculating
			double xRem = screenWidth % width; // remainders
			double yRem = screenHeight % width;
			double[][] field = new double[(int) (screenWidth / width) + 1][(int) (screenHeight / width) + 1];
			int[][] closest = new int[(int) (screenWidth / width) + 1][(int) (screenHeight / width) + 1]; // indexes of closest charges
			for (int i = 0; i < field.length; i++) {
				for (int j = 0; j < field[0].length; j++) {
					// calculating (array index -> actual coords)
					double x = ((((i * width) - (screenWidth / 2) - (xRem / 2) + (width / 2)) / screenScale) + cameraX); // center of square
					double y = ((((j * width) - (screenHeight / 2) - (yRem / 2) + (width / 2)) / screenScale) + cameraY);
					double fieldX = 0;
					double fieldY = 0;
					double minDistance = Double.MAX_VALUE;
					for (int k = 0; k < charges.size(); k++) {
						// getting charge
						Charge charge = charges.get(k);
						// determining distance
						double distance = Math.sqrt(Math.pow(x - charge.getSX(), 2) + Math.pow(y - charge.getSY(), 2));
						if (distance < minDistance) {
							minDistance = distance;
							closest[i][j] = charges.indexOf(charge);
						}
						// calculating components
						fieldX += this.k * (charge.getCharge() / distance) * ((x - charge.getSX()) / distance);
						fieldY += this.k * (charge.getCharge() / distance) * ((y - charge.getSY()) / distance);
					}
					double magnitude = Math.sqrt(Math.pow(fieldX, 2) + Math.pow(fieldY, 2));
					// updating bounds
					if ((firstFrame || relative) && magnitude > max) // initial max calculation
						max = magnitude;
					field[i][j] = magnitude; // adding to field
				}
			}
			// drawing
			if (firstFrame == false) {
				for (int i = 0; i < field.length; i++) {
					for (int j = 0; j < field[0].length; j++) {
						// calculating color
						if (field[i][j] > max) { // to prevent range errors
							field[i][j] = max;
						}
						int color = (int) (Math.pow(field[i][j] / max, 1 / exposure) * 255); // seizure warning
						// setting color
						g.setColor(new Color(color, color, color));
						// inverting color
						if (invert == true) {
							Color oldColor = g.getColor();
							g.setColor(new Color(255 - oldColor.getRed(), 255 - oldColor.getGreen(), 255 - oldColor.getBlue()));
						}
						// calculating location (array index -> screen coords)
						int x = (int) ((i * width) - (screenWidth / 2) - (xRem / 2));
						int y = (int) ((j * width) - (screenHeight / 2) - (yRem / 2));
						g.fillRect(x, y * -1 - width, width + 1, width + 1);
					}
				}
			}
		} else if (visualType == 2) { // arrows
			g.setColor(Color.WHITE);
			if (invert == true)
				g.setColor(Color.BLACK);
			for (int i = (int) ((screenWidth / -2) + (cameraX * screenScale) + ((screenWidth % precision) / 2)); i < ((screenWidth / 2) + (cameraX * screenScale) + 1); i += precision) {
				for (int j = (int) ((screenHeight / -2) + (cameraY * screenScale) + ((screenHeight % precision) / 2)); j < ((screenHeight / 2) + (cameraY * screenScale) + 1); j += precision) {
					// calculating
					double xArrow = 0;
					double yArrow = 0;
					for (int k = 0; k < charges.size(); k++) {
						// getting charge
						Charge charge = charges.get(k);
						// determining distance
						double distance = Math.sqrt(Math.pow(i / screenScale - charge.getSX(), 2) + Math.pow(j / screenScale - charge.getSY(), 2));
						// calculating components
						xArrow += this.k * (charge.getCharge() / Math.pow(distance, 2)) * ((i / screenScale - charge.getSX()) / distance);
						yArrow += this.k * (charge.getCharge() / Math.pow(distance, 2)) * ((j / screenScale - charge.getSY()) / distance);
					}
					double magnitude = Math.sqrt(Math.pow(xArrow, 2) + Math.pow(yArrow, 2));
					// drawing
					xArrow = xArrow / magnitude * arrowLength; // turns it into a vector of arrowLength magnitude
					yArrow = yArrow / magnitude * arrowLength;
					double arrowTheta = Math.atan(yArrow / xArrow);
					if (xArrow < 0)
						arrowTheta += Math.PI;
					// main part
					g.drawLine((int) ((i - xArrow / 2) - cameraX * screenScale), (int) ((j - yArrow / 2) - cameraY * screenScale) * -1, (int) ((i + xArrow / 2) - cameraX * screenScale), (int) ((j + yArrow / 2) - cameraY * screenScale) * -1);
					// pointy part
					int xTip = (int) ((i + xArrow / 2) - cameraX * screenScale);
					int yTip = (int) ((j + yArrow / 2) - cameraY * screenScale) * -1;
					int xLeft = (int) (Math.cos(arrowTheta + (3 * Math.PI / 4)) * arrowLength / 4);
					int yLeft = (int) (Math.sin(arrowTheta + (3 * Math.PI / 4)) * arrowLength / -4);
					g.drawLine(xTip, yTip, xTip + xLeft, yTip + yLeft); // left arrow tip
					int xRight = (int) (Math.cos(arrowTheta - (3 * Math.PI / 4)) * arrowLength / 4);
					int yRight = (int) (Math.sin(arrowTheta - (3 * Math.PI / 4)) * arrowLength / -4);
					g.drawLine(xTip, yTip, xTip + xRight, yTip + yRight); // right arrow tip
				}
			}
		} else if (visualType == 3) { // lines
			// color
			g.setColor(Color.WHITE);
			if (invert == true)
				g.setColor(Color.BLACK);
			// all charges
			for (int i = 0; i < charges.size(); i++) {
				// getting charge
				Charge charge = charges.get(i);
				if (optimize == true && charge.getCharge() < 0) { // shitty optimization
					continue;
				}
				ArrayList<ArrayList<double[]>> lines = charge.lines(charges, numLines, buffer, steps, stepLength, screenScale); // generating lines
				for (int j = 0; j < lines.size(); j++) {
					ArrayList<double[]> line = lines.get(j);
					for (int k = 0; k < line.size() - 1; k++) { // drawing lines
						g.drawLine((int) ((line.get(k)[0] - cameraX) * screenScale), (int) ((line.get(k)[1] - cameraY) * screenScale) * -1, (int) ((line.get(k + 1)[0] - cameraX) * screenScale), (int) ((line.get(k + 1)[1] - cameraY) * screenScale) * -1);
					}
				}

			}
		}

		// drawing charges
		if (chargeView == true) {
			for (int i = 0; i < charges.size(); i++) {
				// getting charge
				Charge charge = charges.get(i);
				// determining color
				g.setColor(Color.GRAY); // neutral
				if (charge.getCharge() > 0) {
					g.setColor(Color.RED); // positive
				} else if (charge.getCharge() < 0) {
					g.setColor(Color.BLUE); // negative
				}
				// actually drawing charge
				g.fillOval((int) ((charge.getSX() - cameraX) * screenScale - radius), (int) ((charge.getSY() - cameraY) * screenScale) * -1 - radius, radius * 2, radius * 2);
			}
		}

		// placing charges
		g.setColor(Color.WHITE);
		if (invert == true) {
			g.setColor(Color.BLACK);
		}
		if (placing == true) {
			location = convert(MouseInfo.getPointerInfo().getLocation());
			int x1 = (int) ((press[0] - cameraX) * screenScale);
			int y1 = (int) ((press[1] - cameraY) * screenScale) * -1;
			int x2 = (int) ((location[0] - cameraX) * screenScale);
			int y2 = (int) ((location[1] - cameraY) * screenScale) * -1;
			g.drawLine(x1, y1, x2, y2);
			// placing info
			double xPlace = location[0] - press[0];
			double yPlace = location[1] - press[1];
			double magnitude = Math.sqrt(Math.pow(xPlace, 2) + Math.pow(yPlace, 2));
			int xUnit = (int) (xPlace / magnitude * 20);
			int yUnit = (int) (yPlace / magnitude * 20);
			g.drawOval(x1 - radius, y1 - radius, radius * 2, radius * 2); // ghost ooo
			// temp calculation
			double vx = (location[0] - press[0]) * chargeSpeed;
			double vy = (location[1] - press[1]) * chargeSpeed;
			if (selectedCharge != null) {
				vx += selectedCharge.getVX();
				vy += selectedCharge.getVY();
			}
			g.drawString("Velocity: {" + Math.round(vx) + ", " + Math.round(vy) + ")", x2 + xUnit, y2 - yUnit);
		}

		// drawing charge bounds
		if (debug == true) {
			for (int i = 0; i < charges.size(); i++) {
				// getting bounds
				Rectangle rectangle = charges.get(i).getBounds(radius, screenScale, cameraX, cameraY);
				// actually drawing bounds
				g.drawRect((int) rectangle.getX(), (int) rectangle.getY(), (int) rectangle.getWidth(), (int) rectangle.getHeight());
			}
		}

		// physics stuff
		if (paused == false) {
			for (int i = 0; i < charges.size(); i++) { // calculating move
				for (int j = 0; j < charges.size(); j++) {
					if (i != j)
						charges.get(i).calculate(charges.get(j));
				}
			}
			for (int i = 0; i < charges.size(); i++) { // executing move
				charges.get(i).step(targetFPS, timeScale);
			}
		}

		// camera stuff
		if (selectedCharge == null) {
			double magnitude = Math.sqrt(Math.pow(cameraDirection[0], 2) + Math.pow(cameraDirection[1], 2));
			if (magnitude != 0.0) {
				double[] unitDirection = { cameraDirection[0] / magnitude, cameraDirection[1] / magnitude }; // unit vector of cameraDirection
				double frameSpeed = cameraSpeed / targetFPS;
				cameraX += unitDirection[0] * frameSpeed / screenScale;
				cameraY += unitDirection[1] * frameSpeed / screenScale;
			}
		} else { // camera follows charge
			cameraX = selectedCharge.getSX();
			cameraY = selectedCharge.getSY();
		}

		// gui / debug
		g.setColor(Color.WHITE);
		if (invert == true)
			g.setColor(Color.BLACK);
		g.translate(screenWidth / -2, screenHeight / -2); // translate back
		g.setFont(new Font("Dialog", Font.PLAIN, 14)); // title font
		g.drawString("Charge Simulator", 10, 20); // program title
		g.setFont(new Font("Dialog", Font.PLAIN, 10)); // subtitle font
		g.drawString("v.2.0", 125, 20); // program version
		g.drawString("by Jason Kim", 10, 35); // program title
		g.setFont(new Font("Dialog", Font.PLAIN, 12)); // regular font
		String warning = "SEIZURE WARNING: This simulation may potentially trigger seizures for people with photosensitive epilepsy. User discretion is advised.";
		int warningWidth = g.getFontMetrics().stringWidth(warning);
		g.drawString(warning, screenWidth / 2 - warningWidth / 2, 20); // dont sue me plz
		g.drawString("Esc - Quit", 10, 75);
		g.drawString("", 10, 95);
		int line = 115; // starting point
		if (debug == false && help == false) { // gui
			g.drawString("H - Help / Controls", 10, line);
			line += 20;
		} else if (debug == false && help == true) { // help
			// general
			ArrayList<String> guiText = new ArrayList<String>();
			guiText.add("Charges:");
			guiText.add("Scenario: " + genType);
			guiText.add("0 - Empty / Clear");
			guiText.add("1 - Random Charges");
			guiText.add("2 - Same Charges");
			guiText.add("3 - Opposite Charges");
			guiText.add("R - Regenerate");
			guiText.add("\\ - Toggle View");
			guiText.add("Alt + Left Click: Add Proton");
			guiText.add("Alt + Right Click: Add Electron");
			Charge guiCharge = null;
			if (selectedCharge == null) {
				guiText.add("No Selected Charge");
			} else {
				guiCharge = selectedCharge;
				guiText.add("Selected Charge:");
				guiText.add("Charge: " + guiCharge.getCharge() + " C");
				guiText.add("Mass: " + guiCharge.getMass() + " kg");
				guiText.add("Location: (" + Math.round(guiCharge.getSX()) + ", " + Math.round(guiCharge.getSY()) + ") m");
				guiText.add("Velocity: (" + Math.round(guiCharge.getVX()) + ", " + Math.round(guiCharge.getVY()) + ") m/s");
				guiText.add("Acceleration: (" + Math.round(guiCharge.getAX()) + ", " + Math.round(guiCharge.getAY()) + ") m/s^2");
			}
			guiText.add("");
			guiText.add("WASD - Translate");
			guiText.add(" + Alt - Slow Translate");
			guiText.add("Location: " + Math.round(cameraX) + ", " + Math.round(cameraY));
			guiText.add("Scroll - Zoom");
			guiText.add("Shift - Zoom In");
			guiText.add("Control - Zoom Out");
			guiText.add("Screen Scale: " + (1 / screenScale) + " meters / pixel");
			guiText.add("");
			guiText.add("Time Scale: x" + timeScale);
			guiText.add("Q - Slow Down");
			guiText.add("E - Speed Up");
			if (paused == true) {
				guiText.add("Paused");
			}
			guiText.add("");
			guiText.add("Electric Field Visualization:");
			guiText.add("");
			guiText.add("Visualization: " + visualType);
			guiText.add("0 - None");
			guiText.add("1 - Color");
			guiText.add("2 - Arrows");
			guiText.add("3 - Lines");
			// color
			ArrayList<String> colorText = new ArrayList<String>();
			colorText.add("Color:");
			colorText.add("Comma - Decrease Area ");
			colorText.add("Period - Increase Area");
			colorText.add("Precision: " + width);
			colorText.add("K - Decrease Exposure");
			colorText.add("L - Increase Exposure");
			colorText.add("Exposure: " + exposure);
			// colorText.add("C - Magnitude: " + this.magnitude);
			// colorText.add("/ - Relative: " + relative);
			// arrows
			ArrayList<String> arrowsText = new ArrayList<String>();
			arrowsText.add("Arrows:");
			arrowsText.add("Comma - Decrease Precision");
			arrowsText.add("Period - Increase Precision");
			arrowsText.add("Precision: " + precision);
			arrowsText.add("N - Decrease Arrow Length");
			arrowsText.add("M - Increase Arrow Length");
			arrowsText.add("Arrow Length: " + arrowLength);
			// lines
			ArrayList<String> linesText = new ArrayList<String>();
			linesText.add("Lines:");
			linesText.add("U - Decrease Lines");
			linesText.add("P - Increase Lines");
			linesText.add("Number of Lines: " + numLines);
			linesText.add("I - Decrease Steps");
			linesText.add("O - Increase Lines");
			linesText.add("Steps/Points: " + steps);
			linesText.add("K - Decrease Step Length");
			linesText.add("L - Increase Step Length");
			linesText.add("Step Length: " + stepLength);
			linesText.add("Y - Optimize: " + optimize);
			// printing
			for (int i = 0; i < guiText.size(); i++) {
				g.drawString(guiText.get(i), 10, line);
				line += 20;
			}
			line += 20;
			if (visualType == 1) {
				for (int i = 0; i < colorText.size(); i++) {
					g.drawString(colorText.get(i), 10, line);
					line += 20;
				}
			} else if (visualType == 2) {
				for (int i = 0; i < arrowsText.size(); i++) {
					g.drawString(arrowsText.get(i), 10, line);
					line += 20;
				}
			} else if (visualType == 3) {
				for (int i = 0; i < linesText.size(); i++) {
					g.drawString(linesText.get(i), 10, line);
					line += 20;
				}
			}
		} else if (debug == true)

		{ // debug
									// to print
			ArrayList<String> debugText = new ArrayList<String>();
			debugText.add("Debug Menu: ON");
			debugText.add("Screen Dimensions: " + screenWidth + " x " + screenHeight);
			debugText.add("Frame Time: " + frameTime);
			debugText.add("Target FPS: " + targetFPS);
			debugText.add("Target Time: " + targetTime);
			debugText.add("Total Frames: " + totalFrames);
			debugText.add("Total Time: " + totalTime);
			debugText.add("Current FPS: " + currentFPS);
			debugText.add("Recent Click: (" + click.getX() + ", " + click.getY() + ")");
			debugText.add("Recent Press: (" + press[0] + ", " + press[1] + ")");
			debugText.add("Mouse Location: (" + location[0] + ", " + location[1] + ")");
			debugText.add("Recent Release: (" + release[0] + ", " + release[1] + ")");
			// printing
			for (int i = 0; i < debugText.size(); i++) {
				g.drawString(debugText.get(i), 10, 75 + 20 * i);
			}
		}

		// frame end
		final long endTime = System.currentTimeMillis();
		frameTime = endTime - startTime;
		if (firstFrame == true)
			firstFrame = false;

		// maintaining fps
		if (frameTime < targetTime) { // ahead
			try {
				TimeUnit.MILLISECONDS.sleep((long) (targetTime - frameTime));
			} catch (InterruptedException e) { // something very wrong has happened
				e.printStackTrace();
			}
		}

		// frame actually ends
		final long finalTime = System.currentTimeMillis();
		frameTime = finalTime - startTime;

		// calculating fps
		totalTime += frameTime;
		totalFrames++;
		if (totalTime >= 1000) { // ~1s intervals
			currentFPS = totalFrames / (totalTime / 1000.0);
			totalTime = 0;
			totalFrames = 0;
		}

		// next frame
		repaint();

	}

	// arrow visuals
	public void changePrecision(double magnitude) {
		precision *= magnitude;
		updateArrowLength();
	}

	// arrow length
	public void updateArrowLength() {
		arrowLength = precision / preclenRatio;
	}

	// key inputs
	class KeyHandler extends KeyAdapter {
		public void keyPressed(KeyEvent e) {
			int keyCode = e.getKeyCode();

			// debug
			if (keyCode == e.VK_F3) {
				debug = !debug;
			}

			// invert
			else if (keyCode == e.VK_CAPS_LOCK) {
				invert = !invert;
			}

			// time
			else if (keyCode == e.VK_SPACE) { // pause
				paused = !paused;
			} else if (keyCode == e.VK_Q) {// slow down time
				timeScale /= 2;
			} else if (keyCode == e.VK_E) {// speed up time
				timeScale *= 2;
			}

			// camera			  up				   left					down				 right
			else if (keyCode == e.VK_W || keyCode == e.VK_A || keyCode == e.VK_S || keyCode == e.VK_D) {
				if (heldKeys.indexOf(keyCode) == -1) { // checks of key is already held
					heldKeys.add(keyCode);
					updateDirection();
					selectedCharge = null; // unselects charge
				}
			} else if (keyCode == e.VK_CONTROL) { // zoom out
				screenScale /= 2;
				steps *= 1.25;
				stepLength *= 1.25;
			} else if (keyCode == e.VK_SHIFT) { // zoom in
				screenScale *= 2;
				steps /= 1.25;
				stepLength /= 1.25;
			}

			// help
			else if (debug != true && keyCode == e.VK_H) {
				help = !help;
			}

			// charges
			else if (keyCode == e.VK_R) {
				genCharges();
			} else if (keyCode == e.VK_BACK_SLASH) {
				chargeView = !chargeView;
			} else if (keyCode == e.VK_ALT) {
				if (heldKeys.indexOf(keyCode) == -1) {
					heldKeys.add(keyCode);
					cameraSpeed /= 4;
				}
			}

			// color
			if (visualType == 1) {
				if (keyCode == e.VK_COMMA) {
					width /= 2;
				} else if (keyCode == e.VK_PERIOD) {
					width *= 2;
				} else if (keyCode == e.VK_K) {
					exposure /= 2;
				} else if (keyCode == e.VK_L) {
					exposure *= 2;
				} else if (keyCode == e.VK_C) {
					magnitude = !magnitude;
				} else if (keyCode == e.VK_SLASH) {
					relative = !relative;
				}
			}

			// arrows
			if (visualType == 2) {
				if (keyCode == e.VK_COMMA) {
					changePrecision(2.0);
				} else if (keyCode == e.VK_PERIOD) {
					changePrecision(0.5);
				} else if (keyCode == e.VK_N) {
					preclenRatio *= 2;
					updateArrowLength();
				} else if (keyCode == e.VK_M) {
					preclenRatio /= 2;
					updateArrowLength();
				}
			}

			// lines
			if (visualType == 3) {
				if (keyCode == e.VK_U) {
					numLines /= 2;
				} else if (keyCode == e.VK_I) {
					steps /= 2;
				} else if (keyCode == e.VK_O) {
					steps *= 2;
				} else if (keyCode == e.VK_P) {
					numLines *= 2;
				} else if (keyCode == e.VK_K) {
					stepLength /= 2;
				} else if (keyCode == e.VK_L) {
					stepLength *= 2;
				} else if (keyCode == e.VK_OPEN_BRACKET) {
					buffer /= 2;
				} else if (keyCode == e.VK_CLOSE_BRACKET) {
					buffer *= 2;
				} else if (keyCode == e.VK_Y) {
					optimize = !optimize;
				}
			}

		}

		public void keyReleased(KeyEvent e) {
			int keyCode = e.getKeyCode();

			// quit
			if (keyCode == e.VK_ESCAPE) {
				System.exit(0);
			}

			// camera
			else if (keyCode == e.VK_W || keyCode == e.VK_A || keyCode == e.VK_S || keyCode == e.VK_D) {
				if (heldKeys.indexOf(keyCode) != -1) { // checks of key is already held
					heldKeys.remove(heldKeys.indexOf(keyCode));
					updateDirection();
				}
			}

			// generation selection
			if (heldKeys.indexOf(KeyEvent.VK_ALT) != -1) {
				if (genType != 0 && keyCode == e.VK_0) {
					genType = 0;
					genCharges();
				} else if (genType != 1 && keyCode == e.VK_1) {
					genType = 1;
					genCharges();
				} else if (genType != 2 && keyCode == e.VK_2) {
					genType = 2;
					genCharges();
				} else if (genType != 3 && keyCode == e.VK_3) {
					genType = 3;
					genCharges();
				}
			}

			// visualization selection
			else if (keyCode == e.VK_0) {
				visualType = 0;
			} else if (keyCode == e.VK_1) {
				if (visualType == 1) {
					visualType = 0;
				} else {
					visualType = 1;
				}
			} else if (keyCode == e.VK_2) {
				if (visualType == 2) {
					visualType = 0;
				} else {
					visualType = 2;
				}
			} else if (keyCode == e.VK_3) {
				if (visualType == 3) {
					visualType = 0;
				} else {
					visualType = 3;
				}
			}

			// placing charges
			if (keyCode == e.VK_ALT) {
				if (heldKeys.indexOf(keyCode) != -1) {
					heldKeys.remove(heldKeys.indexOf(keyCode));
					placing = false;
					cameraSpeed *= 4;
				}
			}

		}
	}

	// when keys are pressed or released
	public void updateDirection() {
		// reset
		cameraDirection[0] = 0;
		cameraDirection[1] = 0;
		// adding directions
		for (int key : heldKeys) {
			if (key == KeyEvent.VK_W) { // up
				cameraDirection[1] += 1;
			} else if (key == KeyEvent.VK_A) { // left
				cameraDirection[0] -= 1;
			} else if (key == KeyEvent.VK_S) { // down
				cameraDirection[1] -= 1;
			} else if (key == KeyEvent.VK_D) { // right
				cameraDirection[0] += 1;
			}
		}
	}

	// mouse inputs
	class MouseHandler extends MouseAdapter {
		public void mouseClicked(MouseEvent e) {
			// getting mouse location
			Point unadjustedClick = e.getLocationOnScreen();
			click = new Point((int) (unadjustedClick.getX() - screenWidth / 2), (int) (unadjustedClick.getY() - screenHeight / 2));
			// converting to sim coords
			double x = (((unadjustedClick.getX() - (screenWidth / 2)) / screenScale) + cameraX);
			double y = (((unadjustedClick.getY() - (screenHeight / 2)) / screenScale) * -1 + cameraY);
			if (heldKeys.indexOf(KeyEvent.VK_ALT) == -1) {
				// selecting charge
				if (e.getButton() == 1) {
					boolean found = false;
					for (int i = 0; i < charges.size(); i++) {
						Charge charge = charges.get(i);
						if (charge.getBounds(radius, screenScale, cameraX, cameraY).contains(click)) {
							found = true;
							selectedCharge = charge; // charge has been selected
							break;
						}
					}
					if (found == false)
						selectedCharge = null; // no charge has been selected / charge has been deselected

				} // deleting charge
				else if (e.getButton() == 3) {
					for (int i = 0; i < charges.size(); i++) {
						Charge charge = charges.get(i);
						if (charge.getBounds(radius, screenScale, cameraX, cameraY).contains(click)) {
							if (charge == selectedCharge)
								selectedCharge = null;
							charges.remove(charges.indexOf(charge)); // deletes charge
						}
					}

				}
			}
		}

		public void mousePressed(MouseEvent e) {
			// for placing charges
			if (heldKeys.indexOf(KeyEvent.VK_ALT) != -1) {
				// getting mouse location
				Point unadjustedClick = e.getLocationOnScreen();
				// converting to sim coords
				double[] convertedClick = convert(unadjustedClick);
				press = convertedClick;
				// start placing
				placing = true;
				if (e.getButton() == 1) {
					double charge = 1.60217663 * Math.pow(10, -19);
					double mass = 1.67262192 * Math.pow(10, -27);
					newCharge = new Charge(charge, mass, convertedClick[0], convertedClick[1], 0.0, 0.0, false);
				} else if (e.getButton() == 3) {
					double charge = -1.60217663 * Math.pow(10, -19);
					double mass = 9.1093837 * Math.pow(10, -31);
					newCharge = new Charge(charge, mass, convertedClick[0], convertedClick[1], 0.0, 0.0, false);
				}
			}
		}

		public void mouseReleased(MouseEvent e) {
			// for placing charges
			if (heldKeys.indexOf(KeyEvent.VK_ALT) != -1) {
				// getting mouse location
				Point unadjustedClick = e.getLocationOnScreen();
				// converting to sim coords
				double[] convertedClick = convert(unadjustedClick);
				release = convertedClick;
				// done placing
				placing = false;
				// time to calculate
				double vx = (release[0] - press[0]) * chargeSpeed;
				double vy = (release[1] - press[1]) * chargeSpeed;
				if (selectedCharge != null) {
					vx += selectedCharge.getVX();
					vy += selectedCharge.getVY();
				}
				// edit velocity
				newCharge.setVX(vx);
				newCharge.setVY(vy);
				charges.add(newCharge);
				newCharge = null;
			}
		}
	}

	public double[] convert(Point unadjustedClick) {
		double x = (((unadjustedClick.getX() - (screenWidth / 2)) / screenScale) + cameraX);
		double y = (((unadjustedClick.getY() - (screenHeight / 2)) / screenScale) * -1 + cameraY);
		double[] convertedClick = { x, y };
		return convertedClick;
	}

	// mouse wheel inputs
	public void mouseWheelMoved(MouseWheelEvent e) {
		// zoom in
		if (e.getWheelRotation() < 0) {
			screenScale *= 2;
		}
		// zoom out
		else if (e.getWheelRotation() > 0) {
			screenScale /= 2;
		}
	}
}
