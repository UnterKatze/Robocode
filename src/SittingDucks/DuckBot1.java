package SittingDucks;

import java.awt.Color;
import static java.awt.Color.*;
import java.io.IOException;

public class DuckBot1 extends DuckBot
{
    @Override
    public void run() 
    {
        Color[] robotColors = {
                    black, yellow, black, yellow, black
                };  // body, gun, radar, bullets, scan-arc

        setColors(robotColors[0], robotColors[1], robotColors[2], robotColors[3], robotColors[4]);

        try 
        {
            broadcastMessage(robotColors);
        } 
        catch (IOException ignored) 
        {
        }

        super.run();

    }
}
