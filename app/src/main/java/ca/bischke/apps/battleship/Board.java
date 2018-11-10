package ca.bischke.apps.battleship;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Board implements Parcelable
{
    private BoardStatus[][] statuses;
    private List<Ship> ships;
    private Random random = new Random();
    private boolean shipsPlaced;

    public Board()
    {
        this.statuses = new BoardStatus[BoardSize.COLUMNS][BoardSize.ROWS];

        for (BoardStatus[] row : statuses)
        {
            Arrays.fill(row, BoardStatus.HIDDEN_EMPTY);
        }

        ships = new ArrayList<>();

        ships.add(new Ship(new Coordinate(0, 0), ShipDirection.VERTICAL, ShipType.CARRIER));
        ships.add(new Ship(new Coordinate(2, 0), ShipDirection.VERTICAL, ShipType.BATTLESHIP));
        ships.add(new Ship(new Coordinate(4, 0), ShipDirection.VERTICAL, ShipType.CRUISER));
        ships.add(new Ship(new Coordinate(6, 0), ShipDirection.VERTICAL, ShipType.SUBMARINE));
        ships.add(new Ship(new Coordinate(8, 0), ShipDirection.VERTICAL, ShipType.DESTROYER));

        shipsPlaced = false;
    }

    public BoardStatus getStatus(int x, int y)
    {
        return statuses[x][y];
    }

    public void setStatus(int x, int y, BoardStatus status)
    {
        statuses[x][y] = status;
    }

    public boolean isStatusHidden(int x, int y)
    {
        if (statuses[x][y] == BoardStatus.HIDDEN_EMPTY || statuses[x][y] == BoardStatus.HIDDEN_SHIP)
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public List<Ship> getShips()
    {
        return ships;
    }

    public int getSmallestRemainingShip()
    {
        int smallest = 5;

        for (Ship ship : ships)
        {
            if (ship.isAlive() && ship.getLength() < smallest)
            {
                smallest = ship.getLength();
            }
        }

        return smallest;
    }

    public int getLongestRemainingShip()
    {
        int longest = 2;

        for (Ship ship : ships)
        {
            if (ship.isAlive() && ship.getLength() > longest)
            {
                longest = ship.getLength();
            }
        }

        return longest;
    }

    public boolean areShipsPlaced()
    {
        return shipsPlaced;
    }

    public void setShipsPlaced(boolean shipsPlaced)
    {
        this.shipsPlaced = shipsPlaced;
    }

    public void placeShipsRandom()
    {
        for (Ship ship : ships)
        {
            boolean valid = false;

            while(!valid)
            {
                int direction = random.nextInt(2);

                if (direction == 0)
                {
                    int x = random.nextInt(BoardSize.COLUMNS - ship.getType().getLength());
                    int y = random.nextInt(BoardSize.ROWS);
                    ship.setDirection(ShipDirection.HORIZONTAL);
                    ship.setCoordinate(new Coordinate(x, y));
                }
                else if (direction == 1)
                {
                    int x = random.nextInt(BoardSize.COLUMNS);
                    int y = random.nextInt(BoardSize.ROWS - ship.getType().getLength());
                    ship.setDirection(ShipDirection.VERTICAL);
                    ship.setCoordinate(new Coordinate(x, y));
                }

                if (!isCollidingWithAny(ship))
                {
                    valid = true;
                }
            }
        }
    }

    private boolean isColliding(Ship ship1, Ship ship2)
    {
        if (ship1 != ship2)
        {
            for (Coordinate coordinate : ship1.getListCoordinates())
            {
                for (Coordinate coordinate2 : ship2.getListCoordinates())
                {
                    if (coordinate.equals(coordinate2))
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean isCollidingWithAny(Ship ship)
    {
        for (Ship ship2 : ships)
        {
            if (isColliding(ship, ship2))
            {
                return true;
            }
        }

        return false;
    }

    public boolean isValidBoard()
    {
        for (Ship ship : ships)
        {
            if (isCollidingWithAny(ship))
            {
                return false;
            }
        }

        return true;
    }

    public void confirmShipLocations()
    {
        if (isValidBoard())
        {
            for (Ship ship : ships)
            {
                for (Coordinate coordinate : ship.getListCoordinates())
                {
                    int x = coordinate.getX();
                    int y = coordinate.getY();

                    setStatus(x, y, BoardStatus.HIDDEN_SHIP);
                }
            }
        }
    }

    public Ship shipToSink()
    {
        for (Ship ship : ships)
        {
            boolean alive = false;

            for (Coordinate coordinate : ship.getListCoordinates())
            {
                int x = coordinate.getX();
                int y = coordinate.getY();

                if (statuses[x][y] != BoardStatus.HIT)
                {
                    alive = true;
                    break;
                }
            }

            if (!alive)
            {
                return ship;
            }
        }

        return null;
    }

    public void sinkShips()
    {
        if (shipToSink() != null)
        {
            Ship ship = shipToSink();

            for (Coordinate coordinate : ship.getListCoordinates())
            {
                int x = coordinate.getX();
                int y = coordinate.getY();

                statuses[x][y] = BoardStatus.SUNK;
                ship.setAlive(false);
            }
        }
    }

    public boolean allShipsSunk()
    {
        for (Ship ship : ships)
        {
            if (ship.isAlive())
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeSerializable(this.statuses);
        dest.writeTypedList(this.ships);
    }

    protected Board(Parcel in)
    {
        this.statuses = (BoardStatus[][]) in.readSerializable();
        this.ships = in.createTypedArrayList(Ship.CREATOR);
    }

    public static final Parcelable.Creator<Board> CREATOR = new Parcelable.Creator<Board>()
    {
        @Override
        public Board createFromParcel(Parcel source)
        {
            return new Board(source);
        }

        @Override
        public Board[] newArray(int size)
        {
            return new Board[size];
        }
    };
}


class BoardSize
{
    public static final int ROWS = 10;
    public static final int COLUMNS = 10;
}


enum BoardStatus
{
    HIDDEN_EMPTY, HIDDEN_SHIP, HIT, MISS, SUNK
}


class Ship implements Parcelable
{
    private boolean alive;
    private Coordinate coordinate;
    private ShipDirection direction;
    private ShipType type;

    public Ship(Coordinate coordinate, ShipDirection direction, ShipType type)
    {
        alive = true;
        this.coordinate = coordinate;
        this.direction = direction;
        this.type = type;
    }

    public boolean isAlive()
    {
        return alive;
    }

    public void setAlive(boolean alive)
    {
        this.alive = alive;
    }

    public Coordinate getCoordinate()
    {
        return coordinate;
    }

    public void setCoordinate(Coordinate coordinate)
    {
        this.coordinate = coordinate;
        fixInvalidCoordinate();
    }

    public List<Coordinate> getListCoordinates()
    {
        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.add(coordinate);

        int x = coordinate.getX();
        int y = coordinate.getY();

        for (int i = 1; i < type.getLength(); i++)
        {
            if (direction == ShipDirection.HORIZONTAL)
            {
                x += 1;
            }
            else if (direction == ShipDirection.VERTICAL)
            {
                y += 1;
            }

            coordinates.add(new Coordinate(x, y));
        }

        return coordinates;
    }

    public Coordinate getCenter()
    {
        int center = ((type.getLength() - 1) / 2);
        return getListCoordinates().get(center);
    }

    public ShipDirection getDirection()
    {
        return direction;
    }

    public void setDirection(ShipDirection direction)
    {
        if (this.direction != direction)
        {
            int center = ((type.getLength() - 1) / 2);
            this.direction = direction;

            if (direction == ShipDirection.HORIZONTAL)
            {
                setCoordinate(new Coordinate(coordinate.getX() - center, coordinate.getY() + center));
            }
            else if (direction == ShipDirection.VERTICAL)
            {
                setCoordinate(new Coordinate(coordinate.getX() + center, coordinate.getY() - center));
            }
        }
    }

    private void fixInvalidCoordinate()
    {
        int x = coordinate.getX();
        int y = coordinate.getY();

        if (x < 0)
        {
            coordinate.setX(0);
        }
        else if (direction == ShipDirection.HORIZONTAL && x + type.getLength() - 1 >= BoardSize.COLUMNS)
        {
            coordinate.setX(BoardSize.COLUMNS - type.getLength());
        }
        else if (direction == ShipDirection.VERTICAL && x >= BoardSize.COLUMNS)
        {
            coordinate.setX(BoardSize.COLUMNS - 1);
        }

        if (y < 0)
        {
            coordinate.setY(0);
        }
        else if (direction == ShipDirection.VERTICAL && y + type.getLength() - 1 >= BoardSize.ROWS)
        {
            coordinate.setY(BoardSize.ROWS - type.getLength());
        }
        else if (direction == ShipDirection.HORIZONTAL && y >= BoardSize.ROWS)
        {
            coordinate.setY(BoardSize.ROWS - 1);
        }
    }

    public void rotate()
    {
        if (direction == ShipDirection.HORIZONTAL)
        {
            setDirection(ShipDirection.VERTICAL);
        }
        else
        {
            setDirection(ShipDirection.HORIZONTAL);
        }
    }

    public ShipType getType()
    {
        return type;
    }

    public int getLength()
    {
        return type.getLength();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeByte(this.alive ? (byte) 1 : (byte) 0);
        dest.writeParcelable(this.coordinate, flags);
        dest.writeInt(this.direction == null ? -1 : this.direction.ordinal());
        dest.writeInt(this.type == null ? -1 : this.type.ordinal());
    }

    protected Ship(Parcel in)
    {
        this.alive = in.readByte() != 0;
        this.coordinate = in.readParcelable(Coordinate.class.getClassLoader());
        int tmpDirection = in.readInt();
        this.direction = tmpDirection == -1 ? null : ShipDirection.values()[tmpDirection];
        int tmpType = in.readInt();
        this.type = tmpType == -1 ? null : ShipType.values()[tmpType];
    }

    public static final Parcelable.Creator<Ship> CREATOR = new Parcelable.Creator<Ship>()
    {
        @Override
        public Ship createFromParcel(Parcel source)
        {
            return new Ship(source);
        }

        @Override
        public Ship[] newArray(int size)
        {
            return new Ship[size];
        }
    };
}


enum ShipDirection
{
    HORIZONTAL, VERTICAL
}


enum ShipType
{
    CARRIER(1, "Aircraft Carrier", 5), BATTLESHIP(2, "Battleship", 4), CRUISER(3, "Cruiser", 3), SUBMARINE(4, "Submarine", 3), DESTROYER(5, "Destroyer", 2);

    private int id;
    private String name;
    private int length;

    ShipType(int id, String name, int length)
    {
        this.id = id;
        this.name = name;
        this.length = length;
    }

    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public int getLength()
    {
        return length;
    }
}
