package SittingDucks;

import java.awt.Color;
import static java.awt.Color.*;
import java.io.IOException;
import static java.lang.Math.PI;
import java.util.HashMap;
import java.util.Set;
import robocode.*;

public class DuckBot4 extends TeamRobot
{

    HashMap<String, ScannedRobots> targets;
    ScannedRobots targetedEnemy;
    double firepower = 1, midpointPower = 250, movingOffset = 0;
    boolean aggressiveMode = false;
    int counter = 0;
    double turnAwayDegrees = 90;
    int scanDirection = 1;

    @Override
    public void run()
    {
        targets = new HashMap<>();
        targetedEnemy = new ScannedRobots();
        targetedEnemy.distance = 9999;
        targetedEnemy.alive = true;
        
        Color[] robotColors =
        {
            black, yellow, black, yellow, black
        };    // body, gun, radar, bullets, scan-arc
        
        setColors(robotColors[0], robotColors[1], robotColors[2], robotColors[3], robotColors[4]); 
        
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true); 
        
        turnRadarLeftRadians(2 * PI); 

        try
        {
            broadcastMessage(robotColors); 
        } catch (IOException ignored)
        {
        }

        while (true)
        {
            if (getTime() % 20 == 0)
            {
               movingOffset = 0; 
//              offset wird für verschieden weit zurückzulegende Distanzen, bspw bei Zusammenstoß mit anderen Bots verändert, 
//              nach 20 Ticks wird er wieder auf 0 gesetzt
            }
            aggressiveMode = ((getOthers() <= 3) && (getTeammates().length == 1));
//              wenn nur noch 2 oder weniger Gegner in der Arena sind und
//              und beide Teamkollegen am Leben sind,
//              wird in einen aggresiven Modus umgeschaltet
            antiGravityMovement(aggressiveMode);
            scanForEnemys();
            calcFirePower();
            calcShootingAngle();
            shoot();
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e)
    {
        if (isTeammate(e.getName())==false)
        {
            scanDirection = -scanDirection;
        }
        ScannedRobots ScEnemy = new ScannedRobots();
        if (targets.containsKey(e.getName()))
        {
            ScEnemy = targets.get(e.getName());
        } else
        {
            targets.put(e.getName(), ScEnemy);
        }
        ScEnemy.name = e.getName();
        ScEnemy.alive = true;
        ScEnemy.isEnemy = !(isTeammate(e.getName()));
        ScEnemy.distance = e.getDistance();
        ScEnemy.currentEnergy = e.getEnergy();
        ScEnemy.headingRadians = e.getHeadingRadians();
        ScEnemy.scanTime = getTime();
        ScEnemy.velocity = e.getVelocity();
        ScEnemy.relativeBearingRadians = e.getBearingRadians();
        ScEnemy.calcAbsoluteBearingRadians(getHeadingRadians());
        ScEnemy.calcPresentXY(getX(), getY());
        
        if ((ScEnemy.distance <= targetedEnemy.distance || (targetedEnemy.alive == false)) && ScEnemy.isEnemy)
        {
            targetedEnemy = ScEnemy;
        }
//        gescannter Robot wird als anzuvisierender Gegner übernommen, 
//        falls er näher als der vorher anvisierte Gegner ist, 
//        oder der andere Gegner abgeschossen wurde
//        und der gescannte Robot kein Teammate ist
    }

    private void scanForEnemys()
    {
        if (aggressiveMode)
        {
            if (getTime() % 20 == 0 ) 
            {
                setTurnRadarRightRadians(2 * PI);    
            }
            else
            {
                setTurnRadarRightRadians(0.25*PI*scanDirection);
//              einen Gegner mit dem Radar verfolgen, wenn <2 Gegner im Spiel sind
            }
        }
        else
        {
            setTurnRadarRightRadians(2 * PI); 
        }

    }

    private void calcFirePower()
    {
        firepower = 600 / targetedEnemy.distance;
        if (firepower < 0.1)
        {
            firepower = 0.1;
        }
        if (firepower > 3.0)
        {
            firepower = 3.0;
        }
        if (targetedEnemy.currentEnergy < 15)
        {
            firepower = firepower * 0.75;
            return;
        }
        if (getEnergy() < 15)
        {
            firepower = firepower * 0.5;
        }
    }

    private void antiGravityMovement(boolean aggressive)
    {
        ScannedRobots GravEnemy;
        ScannedRobots Teammate;
        Teammate = targets.get(getTeammates()[0]);
        double xForce = 0, yForce = 0, force, forceOnTeammate;
        double enemyAbsoluteBearingRadians, movingAngleRadians, movingAngleDegrees;
        final double BattleFieldMidPointX = getBattleFieldWidth() / 2;
        final double BattleFieldMidPointY = getBattleFieldHeight() / 2;
        double[] distanceToMiddleXY, distanceToTeammate;
        Set enemyNames = targets.keySet();

        for (int i = 0; i <= enemyNames.size() - 1; i++)
        {
            GravEnemy = targets.get(targets.keySet().toArray()[i]);
            if (((getTime() - GravEnemy.scanTime) <= 12) && GravEnemy.alive)
            {
                GravityPoint p = new GravityPoint();
                p.pointX = GravEnemy.PresentX;
                p.pointY = GravEnemy.PresentY;
                p.strength = -1250; //hier für aggressiveMode umdrehen?
                enemyAbsoluteBearingRadians = GravEnemy.absoluteBearingRadians;
                force = (p.strength * GravEnemy.calcPriority()) / Math.pow(GravEnemy.distance, 2);
                xForce = Math.sin(enemyAbsoluteBearingRadians) * force + xForce;
                yForce = Math.cos(enemyAbsoluteBearingRadians) * force + yForce;
            } else
            {
                GravEnemy.alive = false; //nicht mehr benötigt?
            }
        }
        distanceToMiddleXY = calcDistance(getX(), getY(), BattleFieldMidPointX, BattleFieldMidPointY);
        distanceToTeammate = calcDistance(getX(), getY(), Teammate.PresentX, Teammate.PresentY);

        counter++;
        if (counter >= 10)
        {
            counter = 0;
            midpointPower = (Math.random() * 500) - 250;
        }

        force = midpointPower / Math.pow(distanceToMiddleXY[2], 2);
        forceOnTeammate = -75 / Math.pow(distanceToTeammate[2], 2);

        if (aggressive)
        {
            xForce = -xForce;
            yForce = -yForce;
          //  force = force / 4; //kraft für agressivemode evtl noch stärker?
        }

        xForce = xForce + force * (BattleFieldMidPointX - getX());
        yForce = yForce + force * (BattleFieldMidPointY - getY());

        xForce = xForce + forceOnTeammate * (Teammate.PresentX - getX());
        yForce = yForce + forceOnTeammate * (Teammate.PresentY - getY());

        if (getX() < 150)
        {
            xForce = xForce + 3000 / Math.pow(getX(), 2);
        }

        if (getX() > (getBattleFieldWidth() - 150))
        {
            xForce = xForce - 3000 / Math.pow(getBattleFieldWidth() - getX(), 2);
        }

        if (getY() < 150)
        {
            yForce = yForce + 3000 / Math.pow(getY(), 2);
        }

        if (getY() > (getBattleFieldHeight() - 150))
        {
            yForce = yForce - 3000 / Math.pow(getBattleFieldHeight() - getY(), 2);
        }

        movingAngleRadians = normaliseAngle(xForce, yForce);
        movingAngleDegrees = Math.toDegrees(movingAngleRadians);

        move(movingAngleDegrees);
    }

    @Override
    public void onHitRobot(HitRobotEvent e)
    {
        turnAwayDegrees = -turnAwayDegrees;
        //move(turnAwayDegrees);
        setTurnLeft(turnAwayDegrees); 
        movingOffset = movingOffset - 90; 
        setAhead(movingOffset);
        
        
    }

    private double normaliseAngle(double x, double y)
    {
        //aus einer X- und einer Y-Koordinate, wird ein winkel zwischen -180 und 180 bzw. -PI und PI berechnet
        //noch hinzufügen->>wie kommen X und Y Koordinaten zustande?
        double angleRadians = 0;
        if ((x >= 0) && (y >= 0))
        {
            angleRadians = Math.abs(Math.atan(x / y));
            return angleRadians;
        }
        if ((x >= 0) && (y <= 0))
        {
            angleRadians = Math.abs(Math.atan(y / x)) + PI / 2;
            return angleRadians;
        }
        if ((x <= 0) && (y <= 0))
        {
            angleRadians = Math.abs(Math.atan(x / y)) + PI;
            return angleRadians;
        }
        if ((x <= 0) && (y >= 0))
        {
            angleRadians = Math.abs(Math.atan(y / x)) + 3 * (PI / 2);
            return angleRadians;
        }
        return angleRadians;
    }

    private void move(double angleDegrees)
    {
        double moveToAngleDegrees;
        double myHeading = getHeading();

        moveToAngleDegrees = angleDegrees - myHeading;

        if (Math.abs(moveToAngleDegrees) > 180)
        {
            if (moveToAngleDegrees > 0)
            {
                moveToAngleDegrees = 360.0 - moveToAngleDegrees;
                setTurnLeft(moveToAngleDegrees);

            } else
            {
                moveToAngleDegrees = 360 - Math.abs(moveToAngleDegrees);
                setTurnRight(moveToAngleDegrees);
            }
        } else
        {
            if (moveToAngleDegrees > 0)
            {
                setTurnRight(moveToAngleDegrees);
            } else
            {
                setTurnLeft(Math.abs(moveToAngleDegrees));
            }
        }
        if ((getX() < 45) || (getX() > (getBattleFieldWidth() - 45)) || (getY() < 45) || (getY() > (getBattleFieldHeight() - 45)))
        {
            ahead(18);
        }
        setAhead(20 + movingOffset);
    }

    private void shoot()
    {
        setFire(firepower);
    }

    private double[] calcDistance(double x1, double y1, double x2, double y2)
    {
        double[] deltas = new double[3];
        deltas[0] = x2 - x1;
        deltas[1] = y2 - y1;
        deltas[2] = Math.sqrt((deltas[0] * deltas[0]) + (deltas[1] * deltas[1]));
        return deltas;
    }

    private void calcShootingAngle()
    {
        double bulletImpactTime, bulletTravelDistanceX, bulletTravelDistanceY;
        double enemyTravelDistance, enemyTravelDistanceX, enemyTravelDistanceY;
        double enemyFuturePositionX, enemyFuturePositionY;
        double bulletHelpingAngleRadians;
        double absoluteShootingAngleRadians = 0, absoluteShootingAngleDegrees, relativeShootingAngle;
        double myGunHeading = getGunHeading();

        bulletImpactTime = getTime() + targetedEnemy.distance / (20 - (3 * firepower));
        enemyTravelDistance = targetedEnemy.velocity * (bulletImpactTime - targetedEnemy.scanTime);
        enemyTravelDistanceX = enemyTravelDistance * Math.sin(targetedEnemy.headingRadians);
        enemyTravelDistanceY = enemyTravelDistance * Math.cos(targetedEnemy.headingRadians);
        enemyFuturePositionX = targetedEnemy.PresentX + enemyTravelDistanceX;
        enemyFuturePositionY = targetedEnemy.PresentY + enemyTravelDistanceY;

        if (enemyFuturePositionX < 0)
        {
            enemyFuturePositionX = 0;
        }
        if (enemyFuturePositionX > getBattleFieldWidth())
        {
            enemyFuturePositionX = getBattleFieldWidth();
        }
        if (enemyFuturePositionY < 0)
        {
            enemyFuturePositionY = 0;
        }
        if (enemyFuturePositionY > getBattleFieldHeight())
        {
            enemyFuturePositionY = getBattleFieldHeight();
        }
        
        bulletTravelDistanceX = enemyFuturePositionX - getX();
        bulletTravelDistanceY = enemyFuturePositionY - getY();

        if ((bulletTravelDistanceX >= 0) && (bulletTravelDistanceY >= 0))
        {
            absoluteShootingAngleRadians = Math.abs(Math.atan(bulletTravelDistanceX / bulletTravelDistanceY));
        }
        if ((bulletTravelDistanceX >= 0) && (bulletTravelDistanceY <= 0))
        {
            absoluteShootingAngleRadians = Math.abs(Math.atan(bulletTravelDistanceX / bulletTravelDistanceY) + PI);
        }
        if ((bulletTravelDistanceX <= 0) && (bulletTravelDistanceY <= 0))
        {
            absoluteShootingAngleRadians = Math.abs(Math.atan(bulletTravelDistanceX / bulletTravelDistanceY) + PI);
        }
        if ((bulletTravelDistanceX <= 0) && (bulletTravelDistanceY >= 0))
        {
            absoluteShootingAngleRadians = Math.abs(Math.atan(bulletTravelDistanceX / bulletTravelDistanceY) + 2 * PI);
        }

        absoluteShootingAngleDegrees = Math.toDegrees(absoluteShootingAngleRadians);

        relativeShootingAngle = absoluteShootingAngleDegrees - myGunHeading;

        if (Math.abs(relativeShootingAngle) > 180)
        {
            if (relativeShootingAngle > 0)
            {
                relativeShootingAngle = 360.0 - relativeShootingAngle;
                setTurnGunLeft(relativeShootingAngle);

            } else
            {
                relativeShootingAngle = 360.0 - Math.abs(relativeShootingAngle);
                setTurnGunRight(relativeShootingAngle);
            }
        } else
        {
            if (relativeShootingAngle > 0)
            {
                setTurnGunRight(relativeShootingAngle);
            } else
            {
                setTurnGunLeft(Math.abs(relativeShootingAngle));
            }
        }
    }
    
    @Override
    public void onRobotDeath(RobotDeathEvent e) 
    {
        targets.get(e.getName()).alive = false;
    }
    
    
}
