package SittingDucks;

import java.awt.Color;
import robocode.*;

public class DuckBot2 extends DuckBot
{
    @Override
    public void onMessageReceived(MessageEvent e)
    {
        if (e.getMessage() instanceof Color[])
        {
            Color[] robotColors = (Color[]) e.getMessage();
            setColors(robotColors[0], robotColors[1], robotColors[2], robotColors[3], robotColors[4]);  
            // body, gun, radar, bullets, scan-arc
        }
    }

}  
