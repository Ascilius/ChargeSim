import java.awt.Rectangle;
import java.util.ArrayList;

// Jason Kim (jasonkim050@gmail.com)

public class Charge {

	// debugging
	private boolean debug = false;

	// physical
	private double charge; // C
	private double mass; // kg
	private double sx, sy; // m
	private double vx, vy; // m/s
	private double ax, ay; // m/s/s
	private double k = 9.0 * Math.pow(10, 9);

	// sim settings
	private boolean locked;

	public Charge(double charge, double mass, double sx, double sy, double vx, double vy, boolean locked) {
		this.charge = charge;
		this.mass = mass;
		this.sx = sx;
		this.sy = sy;
		this.vx = vx;
		this.vy = vy;
		this.ax = 0;
		this.ay = 0;
		this.locked = locked;
	}

	// calculate move
	public void calculate(Charge other) {
		if (locked == false) {
			// reset
			ax = 0;
			ay = 0;
			// calculating
			double distance = Math.sqrt(Math.pow(this.sx - other.getSX(), 2) + Math.pow(this.sy - other.getSY(), 2)); // simple distance formula
			double F = k * other.getCharge() * this.charge / Math.pow(distance, 2); // Coulomb's Law
			ax += F / this.mass * ((this.sx - other.getSX()) / distance); // x component of acceleration
			ay += F / this.mass * ((this.sy - other.getSY()) / distance); // y component
		}
	}

	// actually move
	public void step(int FPS, double timeScale) {
		if (locked == false) {
			// step
			vx += ax / FPS * timeScale;
			vy += ay / FPS * timeScale;
			sx += vx / FPS * timeScale;
			sy += vy / FPS * timeScale;
		}
	}

	// electric field lines
	public ArrayList<ArrayList<double[]>> lines(ArrayList<Charge> charges, int numLines, int buffer, int steps, double stepLength, double screenScale) {
		ArrayList<ArrayList<double[]>> lines = new ArrayList<ArrayList<double[]>>(); // eight starting points, steps amount of points, two components
		for (int i = 0; i < numLines; i++) { // eight starting points
			// new line
			ArrayList<double[]> line = new ArrayList<double[]>();
			// starting point
			double xStart = this.sx + Math.cos((2 * Math.PI * i / numLines) + (2 * Math.PI * 1 / (numLines * 2))) * buffer / screenScale;
			double yStart = this.sy + Math.sin((2 * Math.PI * i / numLines) + (2 * Math.PI * 1 / (numLines * 2))) * buffer / screenScale;
			double[] startPoint = { xStart, yStart };
			if (debug == true) {
				System.out.println("Line " + i + ", Start Point: " + xStart + ", " + yStart);
			}
			line.add(startPoint);
			// following points
			for (int j = 1; j < steps; j++) {
				double[] previousPoint = line.get(j - 1); // getting previous point
				if (debug == true) {
					System.out.println(" Previous Point: " + previousPoint[0] + ", " + previousPoint[1]); // should be the same
				}
				double x = 0;
				double y = 0;
				// electric field
				for (int k = 0; k < charges.size(); k++) {
					Charge charge = charges.get(k);
					double distance = Math.sqrt(Math.pow(previousPoint[0] - charge.getSX(), 2) + Math.pow(previousPoint[1] - charge.getSY(), 2));
					double E = this.k * charge.getCharge() / Math.pow(distance, 2);
					x += E * (previousPoint[0] - charge.getSX()) / distance;
					y += E * (previousPoint[1] - charge.getSY()) / distance;
				}
				// converting to unit vector
				double magnitude = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
				// step (depending on charge)
				if (this.charge > 0) {
					x = x / magnitude * stepLength + previousPoint[0];
					y = y / magnitude * stepLength + previousPoint[1];
				} else if (this.charge < 0) { // negative charge goes backwards
					x = previousPoint[0] - x / magnitude * stepLength;
					y = previousPoint[1] - y / magnitude * stepLength;
				}
				// adding next point
				double[] nextPoint = { x, y };
				if (debug == true) {
					System.out.println(" Next Point: " + x + ", " + y);
				}
				line.add(nextPoint);
			}
			// adding line
			lines.add(line);
		}
		// finally done
		return lines;
	}
	
	// for placing charges
	
	public void setVX(double newVX) {
		this.vx = newVX;
	}

	public void setVY(double newVY) {
		this.vy = newVY;
	}
	
	// general get methods
	
	public double getCharge() {
		return charge;
	}

	public double getMass() {
		return mass;
	}

	public double getSX() {
		return sx;
	}

	public double getSY() {
		return sy;
	}

	public double getVX() {
		return vx;
	}

	public double getVY() {
		return vy;
	}

	public double getAX() {
		return ax;
	}

	public double getAY() {
		return ay;
	}

	// calculates and returns the bounds of the charge in screen coordinates
	public Rectangle getBounds(int radius, double screenScale, double cameraX, double cameraY) {
		int x = (int) ((this.sx - cameraX) * screenScale) - radius;
		int y = (int) ((this.sy - cameraY) * screenScale) * -1 - radius;
		return new Rectangle(x, y, radius * 2, radius * 2);
	}

	public void lock() {
		locked = true;
	}

	public void unlock() {
		locked = false;
	}

	public String toString() {
		return this.charge + " C charge";
	}

}
