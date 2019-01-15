package SittingDucks;

public class ScannedRobots
{

    String name;
    boolean isEnemy;
    boolean alive;
    double distance;
    double currentEnergy;
    double PresentX;
    double PresentY;
    double priority;
    long scanTime;
    double velocity;
    double relativeBearingRadians;
    double headingRadians;
    double absoluteBearingRadians;
    final double PI = Math.PI;

    public String returnName()
    {
        return name;
    }

    public double calcAbsoluteBearingRadians(double myHeadingRadians)
    {
        absoluteBearingRadians = (myHeadingRadians + relativeBearingRadians) % (2 * PI);
        return absoluteBearingRadians;
    }

    public void calcPresentXY(double myX, double myY)
    {
        PresentX = (myX + Math.sin(absoluteBearingRadians) * distance);
        PresentY = (myY + Math.cos(absoluteBearingRadians) * distance);
    }

    public double calcPriority()
    {
        if (currentEnergy < 25)
        {
            return priority = 1.0;
        }
        if (currentEnergy < 50)
        {
            return priority = 1.25;
        }
        if (currentEnergy < 75)
        {
            return priority = 1.5;
        }
        if (currentEnergy < 100)
        {
            return priority = 3.0;
        }
        return priority = 1.0;
    }

    public double[] calcFuturePosition(double deltaTime)
    {
        double[] FuturePos = new double[2];
        FuturePos[0] = PresentX + Math.sin(headingRadians) * velocity * deltaTime;
        FuturePos[1] = PresentY + Math.cos(headingRadians) * velocity * deltaTime;
        return FuturePos;
    }
}
