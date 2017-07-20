package xyz.arwx.strava;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static xyz.arwx.strava.Units.UnitType.Metric;

/**
 * Created by macobas on 21/07/17.
 */
public class Units
{
    public enum UnitType
    {
        Imperial,
        Metric
    }

    // Feet per meter
    public final double FeetPerMeter = 3.28084;
    public final int FeetPerMile = 5280;

    public enum Unit
    {
        Mile("mi"),
        Kilometer("km"),
        Meter("m"),
        Feet("ft");

        private String abbrev;
        Unit(String abbrev)
        {
            this.abbrev = abbrev;
        }

        public String abbrev() { return abbrev; }
    }

    UnitType fromType;
    UnitType toType;

    public UnitType toType() { return toType; }

    public static Units of(UnitType toType)
    {
        Units units = new Units();
        units.fromType = Metric;
        units.toType = toType;
        return units;
    }

    public Unit distanceUnit()
    {
        switch(toType)
        {
            case Imperial:
                return Unit.Mile;
            case Metric:
                return Unit.Kilometer;
        }

        return null;
    }

    public Unit elevUnit()
    {
        switch(toType)
        {
            case Imperial:
                return Unit.Feet;
            case Metric:
                return Unit.Meter;
        }

        return null;
    }

    public double getDistance(double inDist)
    {
        switch(toType)
        {
            case Imperial:
                return (inDist * FeetPerMeter) / (double)FeetPerMile;
            case Metric:
                return inDist / 1000.;
        }

        return inDist;
    }

    public double getElevation(double inDist)
    {
        switch(toType)
        {
            case Imperial:
                return inDist * FeetPerMeter;
            case Metric:
                return inDist;
        }

        return inDist;
    }
}
