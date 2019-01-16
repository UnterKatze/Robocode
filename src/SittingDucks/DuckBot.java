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
        // Distanz des ersten "Gegeners" ist 9999, damit dieser nicht fälschlicherweise am Anfang anvisiert wird.
        
        targetedEnemy.alive = true;
        
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true); 
        
        turnRadarLeftRadians(2 * PI); 
        // Zu Beginn wird einmal ein 360 Grad scan durchgeführt, um alle Gegner zu erfassen

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
        if (!isTeammate(e.getName()))
        {
            scanDirection = -scanDirection;
            // für kontinuierliches Anvisieren im AgressiveMode
        }
        
        ScannedRobots ScEnemy = new ScannedRobots();
        if (targets.containsKey(e.getName()))
        {
            ScEnemy = targets.get(e.getName()); 
        } else
        {
            targets.put(e.getName(), ScEnemy);
        }
        // Check ob der gescannte Gegner schon früher gescannt wurde
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
        // Werte werden durch Verlinkung direkt in der Hashmap für entsprechenden Robot aktualisiert
        
        if ((ScEnemy.distance <= targetedEnemy.distance || (targetedEnemy.alive == false)) && ScEnemy.isEnemy)
        {
            targetedEnemy = ScEnemy;
            // Wenn der jetzt gescannte Gegner näher als der nähste vorher gescannte Gegner ist, wird dieser anvisiert
        }
    }

    protected void scanForEnemys()
    {
        if ((getOthers() == 1) || (getOthers() == 2 && teamMateAlive)) 
        {
            setTurnRadarRightRadians(0.20*PI*scanDirection);
            // einen Gegner mit dem Radar verfolgen
            if (getTime() % 20 == 0)
            {
                setTurnRadarRightRadians(2 * PI);
            }
        } else
        {
            setTurnRadarRightRadians(2 * PI);
        }
    }
    
    /* 
        Diese Methode berechnet die Geschossgröße anhand des Abstandes zum anvisierten
        Gegner. Die Geschossgröße wird außerdem bei geringer Eigen- sowie Gegnerenergie
        verringert.
    */
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
    
    /*
        Alle Bewegungsabläufe der Roboter werden hier berechnet. Es werden Anziehungs- oder
        Abstoßungspunkte anhand der Position der Gegnerroboter, der Spielfeldmitte und der
        Spielfeldwände generiert.
    */
    protected void antiGravityMovement(boolean aggressive)
    {
        ScannedRobots GravEnemy;
        ScannedRobots Teammate;
        
        double xForce = 0, yForce = 0, force;
        
        if (targets.get(getTeammates()) != null)
        {
            Teammate = targets.get(getTeammates()[0]);
            // Informationen über den Teamroboter werden temporär übertragen
            double[] distanceToTeammate = calcDistance(getX(), getY(), Teammate.PresentX, Teammate.PresentY);
            double forceOnTeammate = -75 / Math.pow(distanceToTeammate[2], 2);
            // Abstoßung von Teammate wird noch einmal extra gewichtet
        
            xForce = xForce + forceOnTeammate * (Teammate.PresentX - getX());
            yForce = yForce + forceOnTeammate * (Teammate.PresentY - getY());
            // Abstoßung von Teammate wird hinzugefügt
        }
        
        final double BattleFieldMidPointX = getBattleFieldWidth() / 2;
        final double BattleFieldMidPointY = getBattleFieldHeight() / 2;
        
        Set enemyNames = targets.keySet(); 
        // liste der Enemynames

        for (int i = 0; i <= enemyNames.size() - 1; i++) 
        {
            GravEnemy = targets.get(targets.keySet().toArray()[i]);
            // für jeden noch lebenden Robot wird ein GravityPoint erstellt
            
            if (GravEnemy.alive) 
            {
                GravityPoint p = new GravityPoint();
                p.pointX = GravEnemy.PresentX;
                p.pointY = GravEnemy.PresentY;
                p.strength = -1250;
                
                double enemyAbsoluteBearingRadians = GravEnemy.absoluteBearingRadians;
                
                force = (p.strength * GravEnemy.calcPriority()) / Math.pow(GravEnemy.distance, 2); 
                xForce = Math.sin(enemyAbsoluteBearingRadians) * force + xForce; 
                yForce = Math.cos(enemyAbsoluteBearingRadians) * force + yForce;
                // Abstoßungskraft in x und y Richtung wird für jeden Gegner berechnet und aufaddiert
            }
        }
        
        double[] distanceToMiddleXY = calcDistance(getX(), getY(), BattleFieldMidPointX, BattleFieldMidPointY); 
        // Abstand zur Spielfeldmitte wird berechnet
        
        counter++;
        if (counter >= 10)
        {
            counter = 0;
            midpointPower = (Math.random() * 500) - 250;
            // Berechnung der zufälligen Anziehungs-/ Anstoßungskraft vom Mittelpunkt von -250 bis +250
        }

        force = midpointPower / Math.pow(distanceToMiddleXY[2], 2);
        
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
        // Abstoßung oder Anziehung vom Mittelpunkt wird zu dem errechnetem Wert hinzugefügt
        
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
        // wenn der Robot einer Wand zu nahe kommt, wird der Kraft eine Komponente in X- bzw in Y-Richtung hinzugefügt, die von der Wand weggerichtet ist
        // je näher der Robot zur Wand ist desto stärker wird er abgestoßen
       
        double movingAngleRadians = normaliseAngle(xForce, yForce);
        double movingAngleDegrees = Math.toDegrees(movingAngleRadians);
        // Aus der X und Y Komponente der Kraft wird der Winkel in Grad errechnet
        
        move(movingAngleDegrees);
    }

    @Override
    public void onHitRobot(HitRobotEvent e)
    {
        setTurnLeft(turnAwayDegrees); 
        movingOffset = movingOffset - 90; 
        setAhead(movingOffset);
    }

    /*
        Aus der X und der Y Komponente eines Vektors wird der Winkel zwischen -PI bis PI berechnet
    */
    protected double normaliseAngle(double x, double y)
    {
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

    /*
        Mit dem übergebenen Winkel wird die Fahrtrichtung für den nächsten Zug berechnet.
        Wenn der Roboter einer Wand zu nahe kommt wird sofort abgebremst.
    */
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

    /*
        Diese Methode berechnet Abstände zwischen zwei Punkten in einer X-Y Ebene.
        Sie gibt ein Array der Länge 3 zurück mit Informationen über deltaX, deltaY und
        dem Abstand der zwei Punkte.
    */
    protected double[] calcDistance(double x1, double y1, double x2, double y2)
    {
        double[] deltas = new double[3];
        deltas[0] = x2 - x1;
        deltas[1] = y2 - y1;
        deltas[2] = Math.sqrt((deltas[0] * deltas[0]) + (deltas[1] * deltas[1]));
        return deltas;
    }

    /*
        Hier wird die vorraussichtliche Gegnerposition ein einem zukünftigen Zug berechnet und
        die Kanone in diese Richtung gestellt. Die Berechnungen gehen von einer linearen
        Bewegung des Gegnerroboters mit gleichbleibender Geschwindigkeit aus.
    */
    protected void calcShootingAngle()
    {
        double absoluteShootingAngleRadians = 0;
        double myGunHeading = getGunHeading();
        double bulletImpactTime = getTime() + targetedEnemy.distance / (20 - (3 * firepower));
        double enemyTravelDistance = targetedEnemy.velocity * (bulletImpactTime - targetedEnemy.scanTime);
        double enemyTravelDistanceX = enemyTravelDistance * Math.sin(targetedEnemy.headingRadians);
        double enemyTravelDistanceY = enemyTravelDistance * Math.cos(targetedEnemy.headingRadians);
        double enemyFuturePositionX = targetedEnemy.PresentX + enemyTravelDistanceX;
        double enemyFuturePositionY = targetedEnemy.PresentY + enemyTravelDistanceY;

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
        
        double bulletTravelDistanceX = enemyFuturePositionX - getX();
        double bulletTravelDistanceY = enemyFuturePositionY - getY();

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

        double absoluteShootingAngleDegrees = Math.toDegrees(absoluteShootingAngleRadians);
        double relativeShootingAngle = absoluteShootingAngleDegrees - myGunHeading;

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