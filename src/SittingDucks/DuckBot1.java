package SittingDucks;

import java.awt.Color;
import static java.awt.Color.*;
import java.io.IOException;

public class DuckBot1 extends DuckBot
{
    @Override
    public void run() 
    {
        Color[] robotColors = { black, yellow, black, yellow, black };
        
        setColors(robotColors[0], robotColors[1], robotColors[2], robotColors[3], robotColors[4]);
        // Teamfarben für body, gun, radar, bullets und scan-arc werden gesetzt

        try 
        {
            broadcastMessage(robotColors);
        } 
        catch (IOException ignored) 
        {
        }
        // Teamfarben werden versucht dem Teamkollegen zu übermitteln

        super.run();
        // Die run() Methode der abgeleiteten Klasse wird ausgeführt
    }
}
