package me.polymorphicagent.crystalspotlights;

/// Defines an angle in degrees from a vertical line
/// If no angle defined in constructor, default is 0, 0
public class ThreeDimensionalAngle {

    //horAngle is an angle in the xz plane, vertAngle is an angle from the y-axis towards the xy plane
    //format as of this class is horAngle, vertAngle, but in almost all the pre-class math this is flipped 😬
    //hey, at least it works...
    private final double horAngle;
    private final double vertAngle;
    private double[] endpoint;
    CrystalSpotlights plugin;

    public ThreeDimensionalAngle(double horAngle, double vertAngle, CrystalSpotlights plugin){
        this.horAngle = horAngle;
        this.vertAngle = vertAngle;
        this.plugin = plugin;
    }

    public ThreeDimensionalAngle(int[] angle, CrystalSpotlights plugin){
        this.horAngle = angle[0];
        this.vertAngle = angle[1];
        this.plugin = plugin;
    }

    public double[] getEndpoint() { return endpoint; }


    public void calculateRayEndpoint(double[] rayStartPoint){
        if(horAngle == 0) {
            endpoint = new double[]{rayStartPoint[0], rayStartPoint[1]+256, rayStartPoint[2]};
            return;
        }

        //defines how far in the xz plane
        double r = 50;

        //defines how far up
        double h = 256;

        //define our bounds
        double xUpperBound = rayStartPoint[0] + r;
        double xLowerBound = rayStartPoint[0] - r;
        double yUpperBound = h - rayStartPoint[1];
        double zUpperBound = rayStartPoint[2] + r;
        double zLowerBound = rayStartPoint[2] - r;

        //convert angles from degrees to radians (manually swapped?!?!?)
        double vertRadians = Math.toRadians(horAngle);  //angle from y-axis to xz plane
        double horRadians = Math.toRadians(vertAngle);    //angle within xz plane from positive x-axis

        //calculate direction components
        double yComponent = Math.cos(vertRadians);  //component along the y-axis
        double horizontalMagnitude = Math.sin(vertRadians);  //magnitude in the xz plane
        double xComponent = horizontalMagnitude * Math.cos(horRadians);  //x-axis projection
        double zComponent = horizontalMagnitude * Math.sin(horRadians);  //z-axis projection

        //calculate max scale to keep endpoint within bounds
        double maxScaleX = (xComponent >= 0) ? (xUpperBound - rayStartPoint[0]) / xComponent : (rayStartPoint[0] - xLowerBound) / -xComponent;
        double maxScaleY = (yComponent >= 0) ? yUpperBound / yComponent : rayStartPoint[1] / -yComponent;
        double maxScaleZ = (zComponent >= 0) ? (zUpperBound - rayStartPoint[2]) / zComponent : (rayStartPoint[2] - zLowerBound) / -zComponent;
        double maxScale = Math.min(Math.min(maxScaleX, maxScaleY), maxScaleZ);

        endpoint = new double[]{xComponent * maxScale + rayStartPoint[0], yComponent * maxScale + rayStartPoint[1], zComponent * maxScale + rayStartPoint[2]};
    }

}
