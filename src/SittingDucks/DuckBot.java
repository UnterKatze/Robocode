package SittingDucks;

import static java.lang.Math.PI;
import java.util.HashMap;
import java.util.Set;
import robocode.*;

public class DuckBot extends TeamRobot
{
    HashMap<String, ScannedRobots> targets;
    // gescannte Roboter werden in synchroniserter Liste abgelegt
    
    ScannedRobots targetedEnemy;
    // aktuell anvisierter Gegner
    
    double firepower = 1, midpointPower = 250, movingOffset = 0;
    boolean aggressiveMode = false;
    // sind nur noch 2 Gegner am Leben werden diese aggressiver verfolgt und beschossen
    
    boolean teamMateAlive = true;
    int counter = 0;
    // alle 10 counts wird die zufällige Anziehungs-, bzw. Abstoßungskraft neu berechnet
    
    double turnAwayDegrees = 90;
    int scanDirection = 1;
    // für das kontinuierliche Anvisieren eines Gegners, wird die Scan-Richtung zwischen 1 und -1 hin und hergeschaltet,
    // sobald ein neues onScannedRobotEvent erzeugt wird

    @Override
    public void run()
    {
        targets = new HashMap<>();
        targetedEnemy = new ScannedRobots();
        targetedEnemy.distance = 9999;
        targetedEnemy.alive = true;
        
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true); 
        
        turnRadarLeftRadians(2 * PI); 

        while (true)
        {
            if (getTime() % 20 == 0)
            {
               movingOffset = 0; 
               // offset wird für verschieden weit zurückzulegende Distanzen, bspw bei Zusammenstoß mit anderen Bots verändert, 
               // nach 20 Ticks wird er wieder auf 0 gesetzt
            }
            aggressiveMode = (((getOthers() <= 3) && teamMateAlive)||(getOthers()== 1));

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
        // für aimlock in AgressiveMode
        if (!isTeammate(e.getName()))
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
        // Werte werden durch Verlinkung direkt auch in Hashmap für entsprechenen Robot aktualisiert
        
        if ((ScEnemy.distance <= targetedEnemy.distance || (targetedEnemy.alive == false)) && ScEnemy.isEnemy)
        {
            targetedEnemy = ScEnemy;
        }
    }

    protected void scanForEnemys()
    {
        if (aggressiveMode)
        {
            if ((getTime() % 20 == 0) && (getOthers() > 1))  
            {
                setTurnRadarRightRadians(2 * PI);    
            }
            else
            {
                setTurnRadarRightRadians(0.20*PI*scanDirection);
                // einen Gegner mit dem Radar verfolgen
            }
        }
        else
        {
            setTurnRadarRightRadians(2 * PI); 
        }
    }

    protected void calcFirePower()
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

    protected void antiGravityMovement(boolean aggressive)
    {
        ScannedRobots GravEnemy;
        ScannedRobots Teammate;
        
        Teammate = targets.get(getTeammates()[0]); //teammate wird doppelt in gravitypoint-berechnung mit einbezogen?
        
        double xForce = 0, yForce = 0, force, forceOnTeammate;
        
        double enemyAbsoluteBearingRadians, movingAngleRadians, movingAngleDegrees;
        
        final double BattleFieldMidPointX = getBattleFieldWidth() / 2;
        final double BattleFieldMidPointY = getBattleFieldHeight() / 2;
        
        double[] distanceToMiddleXY, distanceToTeammate;
        
        Set enemyNames = targets.keySet(); 
        // liste der Enemynames

        // für jeden noch lebenden Robot wird ein GravityPoint erstellt 
        for (int i = 0; i <= enemyNames.size() - 1; i++) 
        {
            GravEnemy = targets.get(targets.keySet().toArray()[i]);
            
            if (GravEnemy.alive) 
            {
                GravityPoint p = new GravityPoint();
                p.pointX = GravEnemy.PresentX;
                p.pointY = GravEnemy.PresentY;
                p.strength = -1250;
                
                enemyAbsoluteBearingRadians = GravEnemy.absoluteBearingRadians;
                
                force = (p.strength * GravEnemy.calcPriority()) / Math.pow(GravEnemy.distance, 2); 
                xForce = Math.sin(enemyAbsoluteBearingRadians) * force + xForce; 
                yForce = Math.cos(enemyAbsoluteBearingRadians) * force + yForce;
                // Abstoßungskraft in x und y--Richtung wird für jeden Gegner berechnet und aufaddiert
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
        // Abstoßung von Teammate wird noch einmal extra gewichtet

        if (aggressive)
        {
            xForce = -xForce;
            yForce = -yForce;
            // unser Robot wird von den Gegnern angezogen, anstatt abgestoßen 
            
            force = force / 4; 
            // Anziehungskraft von Mittelpunkt wird kleiner, dadurch wird die Bewegungsrichtung mehr von den Gegnerrobotern bestimmt
        }
        
        xForce = xForce + force * (BattleFieldMidPointX - getX());
        yForce = yForce + force * (BattleFieldMidPointY - getY());
        // Abstoßung von Mittelpunkt wird zu errechnetem Wert hinzugefügt
        
        xForce = xForce + forceOnTeammate * (Teammate.PresentX - getX());
        yForce = yForce + forceOnTeammate * (Teammate.PresentY - getY());
        // Abstoßung von Teammate wird hinzugefügt

       
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
        // wenn der Robot einer Wand zu nahe kommt wird der Kraft eine Komponente in X- bzw in Y-Richtung hinzugefügt, die von der Wand weggerichtet ist
        // je näher der Robot zur Wand ist desto stärker wird er abgestoßen
       
        movingAngleRadians = normaliseAngle(xForce, yForce);
        movingAngleDegrees = Math.toDegrees(movingAngleRadians);
        //aus den X- und Y-Kompenten wird ein ein Winkel für die Bewegungsrichtung des Robots ausgerechnet 
        
        move(movingAngleDegrees);
    }

    @Override
    public void onHitRobot(HitRobotEvent e)
    {
        setTurnLeft(turnAwayDegrees); 
        movingOffset = movingOffset - 90; 
        setAhead(movingOffset);
    }

    protected double normaliseAngle(double x, double y)
    {
        // aus einer X- und einer Y-Koordinate, wird ein winkel zwischen -180 und 180 bzw. -PI und PI berechnet
        
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

    protected void move(double angleDegrees)
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

    protected void shoot()
    {
        setFire(firepower);
    }

    protected double[] calcDistance(double x1, double y1, double x2, double y2)
    {
        //berechnet den Abstand zwischen zwei Punkten im Koordinatensystem 
        //gibt deltaX, deltaY, und den direkten Abstand zwischen den zwei Punkten zurück
        double[] deltas = new double[3];
        deltas[0] = x2 - x1;
        deltas[1] = y2 - y1;
        deltas[2] = Math.sqrt((deltas[0] * deltas[0]) + (deltas[1] * deltas[1]));
        return deltas;
    }

    protected void calcShootingAngle()
    {
        double bulletImpactTime, bulletTravelDistanceX, bulletTravelDistanceY;
        double enemyTravelDistance, enemyTravelDistanceX, enemyTravelDistanceY;
        double enemyFuturePositionX, enemyFuturePositionY;
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
         if (isTeammate(e.getName()))
                {
                    teamMateAlive = false;
                }
         targets.get(e.getName()).alive = false;
    }   
}