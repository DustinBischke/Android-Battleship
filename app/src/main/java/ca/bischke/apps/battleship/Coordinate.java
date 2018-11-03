package ca.bischke.apps.battleship;

import android.os.Parcel;
import android.os.Parcelable;

public class Coordinate implements Parcelable
{
    private int x;
    private int y;

    public Coordinate(int x, int y)
    {
        this.x = x;
        this.y = y;
    }

    public int getX()
    {
        return x;
    }

    public void setX(int x)
    {
        this.x = x;
    }

    public int getY()
    {
        return y;
    }

    public void setY(int y)
    {
        this.y = y;
    }

    public void set(int x, int y)
    {
        this.x = x;
        this.y = y;
    }
    
    @Override
    public boolean equals(Object object)
    {
        if (!(object instanceof Coordinate))
        {
            return false;
        }
        
        Coordinate c = (Coordinate) object;

        if (this.x == c.getX() && this.y == c.getY())
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeInt(this.x);
        dest.writeInt(this.y);
    }

    protected Coordinate(Parcel in)
    {
        this.x = in.readInt();
        this.y = in.readInt();
    }

    public static final Parcelable.Creator<Coordinate> CREATOR = new Parcelable.Creator<Coordinate>()
    {
        @Override
        public Coordinate createFromParcel(Parcel source)
        {
            return new Coordinate(source);
        }

        @Override
        public Coordinate[] newArray(int size)
        {
            return new Coordinate[size];
        }
    };
}