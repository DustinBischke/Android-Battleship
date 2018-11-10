package ca.bischke.apps.battleship;

import android.annotation.SuppressLint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@SuppressLint("ViewConstructor")

public class AI
{
    private List<Probability> probabilities;
    private AIDifficulty difficulty;
    private Board board;
    private Random random = new Random();

    public AI(AIDifficulty difficulty, Board board)
    {
        this.difficulty = difficulty;
        this.board = board;
        probabilities = new ArrayList<>();
        initializeProbabilities();
    }

    private List<Coordinate> allCoordinates()
    {
        List<Coordinate> coordinates = new ArrayList<>();

        for (int i = 0; i < BoardSize.COLUMNS; i++)
        {
            for (int j = 0; j < BoardSize.ROWS; j++)
            {
                coordinates.add(new Coordinate(i, j));
            }
        }

        return coordinates;
    }

    private void initializeProbabilities()
    {
        if (difficulty == AIDifficulty.EASY || difficulty == AIDifficulty.MEDIUM)
        {
            for (int i = 0; i < BoardSize.COLUMNS; i++)
            {
                for (int j = 0; j < BoardSize.ROWS; j++)
                {
                    probabilities.add(new Probability(new Coordinate(i, j), 1));
                }
            }
        }
        else if (difficulty == AIDifficulty.HARD)
        {
            probabilities = calculateProbabilities(checkerboardCoordinates());
        }
    }

    private Probability calculateProbability(Coordinate coordinate)
    {
        int x = coordinate.getX();
        int y = coordinate.getY();
        int probability = 0;
        int longestShip = board.getLongestRemainingShip();

        for (int i = x - 1; i > 0 && i > x - longestShip; i--)
        {
            if (board.isStatusHidden(i, y))
            {
                probability += 1;
            }
            else
            {
                break;
            }
        }

        for (int i = x + 1; i < 9 && i < x + longestShip; i++)
        {
            if (board.isStatusHidden(i, y))
            {
                probability += 1;
            }
            else
            {
                break;
            }
        }

        for (int i = y - 1; i > 0 && i > y - longestShip; i--)
        {
            if (board.isStatusHidden(x, i))
            {
                probability += 1;
            }
            else
            {
                break;
            }
        }

        for (int i = y + 1; i < 9 && i < y + longestShip; i++)
        {
            if (board.isStatusHidden(x, i))
            {
                probability += 1;
            }
            else
            {
                break;
            }
        }

        return new Probability(coordinate, probability);
    }

    private List<Probability> calculateProbabilities(List<Coordinate> coordinates)
    {
        List<Probability> probabilities = new ArrayList<>();

        for (Coordinate coordinate : coordinates)
        {
            probabilities.add(calculateProbability(coordinate));
        }

        return sortProbabilities(probabilities);
    }

    private List<Probability> sortProbabilities(List<Probability> probabilities)
    {
        Collections.sort(probabilities, new Comparator<Probability>()
        {
            @Override
            public int compare(Probability p1, Probability p2)
            {
                if (p1.getProbability() > p2.getProbability())
                {
                    return -1;
                }
                else if (p1.getProbability() < p2.getProbability())
                {
                    return 1;
                }
                else
                {
                    return 0;
                }
            }
        });

        return probabilities;
    }

    private Coordinate highestProbabilityCoordinate()
    {
        sortProbabilities(probabilities);

        List<Coordinate> mostLikelyCoordinates = new ArrayList<>();
        int highestProbability = probabilities.get(0).getProbability();

        for (Probability probability : probabilities)
        {
            if (probability.getProbability() == highestProbability)
            {
                mostLikelyCoordinates.add(probability.getCoordinate());
            }
            else
            {
                break;
            }
        }

        int index = random.nextInt(mostLikelyCoordinates.size());
        return mostLikelyCoordinates.get(index);
    }

    // Returns True if Squares to the right of Coordinate are Hidden
    private int spaceAroundSquareXPositive(Coordinate coordinate)
    {
        int space = 0;
        int x = coordinate.getX();
        int y = coordinate.getY();

        for (int i = x + 1; i < BoardSize.COLUMNS; i++)
        {
            if (board.isStatusHidden(i, y))
            {
                space += 1;
            }
            else
            {
                break;
            }
        }

        return space;
    }

    // Returns True if Squares below Coordinate are Hidden
    private int spaceAroundSquareYPositive(Coordinate coordinate)
    {
        int space = 0;
        int x = coordinate.getX();
        int y = coordinate.getY();

        for (int i = y + 1; i < BoardSize.ROWS; i++)
        {
            if (board.isStatusHidden(x, i))
            {
                space += 1;
            }
            else
            {
                break;
            }
        }

        return space;
    }

    // Returns True if there are any Hits on the Board
    private boolean knowsAnyShipLocation()
    {
        for (int i = 0; i < BoardSize.COLUMNS; i++)
        {
            for (int j = 0; j < BoardSize.ROWS; j++)
            {
                if (board.getStatus(i, j) == BoardStatus.HIT)
                {
                    return true;
                }
            }
        }

        return false;
    }

    // Returns List of Coordinates in Checkerboard formation
    private List<Coordinate> checkerboardCoordinates()
    {
        List<Coordinate> coordinates = new ArrayList<>();
        int random = (int)Math.round(Math.random());

        for (int i = 0; i < BoardSize.COLUMNS; i++)
        {
            for (int j = 0; j < BoardSize.ROWS; j++)
            {
                // Creates 2 options for Checkerboard Targeting
                if (random == 0)
                {
                    if ((i % 2 == 0 && j % 2 == 0) || (i % 2 == 1 && j % 2 == 1))
                    {
                        coordinates.add(new Coordinate(i, j));
                    }
                }
                else
                {
                    if ((i % 2 == 0 && j % 2 == 1) || (i % 2 == 1 && j % 2 == 0))
                    {
                        coordinates.add(new Coordinate(i, j));
                    }
                }
            }
        }

        return coordinates;
    }

    // Returns List of all Hidden Coordinates
    private List<Coordinate> allHiddenCoordinates()
    {
        List<Coordinate> hiddenCoordinates = new ArrayList<>();

        for (int i = 0; i < BoardSize.COLUMNS; i++)
        {
            for (int j = 0; j < BoardSize.ROWS; j++)
            {
                if (board.isStatusHidden(i, j))
                {
                    hiddenCoordinates.add(new Coordinate(i, j));
                }
            }
        }

        return hiddenCoordinates;
    }

    // Returns List of all Coordinates that could hold one of the remaining Ships
    private List<Coordinate> allPossibleShipCoordinates()
    {
        List<Coordinate> possibleCoordinates = new ArrayList<>();

        for (Ship ship : board.getShips())
        {
            if (ship.isAlive())
            {
                for (int i = 0; i < BoardSize.COLUMNS; i++)
                {
                    for (int j = 0; j < BoardSize.ROWS; j++)
                    {
                        if (board.isStatusHidden(i, j))
                        {
                            Coordinate coordinate = new Coordinate(i, j);

                            if (spaceAroundSquareXPositive(coordinate) >= ship.getLength() - 1)
                            {
                                for (int k = 0; k < ship.getLength(); k++)
                                {
                                    possibleCoordinates.add(new Coordinate(i + k, j));
                                }
                            }

                            if (spaceAroundSquareYPositive(coordinate) >= ship.getLength() - 1)
                            {
                                for (int k = 0; k < ship.getLength(); k++)
                                {
                                    possibleCoordinates.add(new Coordinate(i, j + k));
                                }
                            }
                        }
                    }
                }
            }
        }

        Collections.sort(possibleCoordinates, new Comparator<Coordinate>()
        {
            @Override
            public int compare(Coordinate c1, Coordinate c2)
            {
                if (c1.getY() == c2.getY())
                {
                    if (c1.getX() < c2.getX())
                    {
                        return -1;
                    }
                    else if (c1.getX() > c2.getX())
                    {
                        return 1;
                    }
                    else
                    {
                        return 0;
                    }
                }
                else if (c1.getY() < c2.getY())
                {
                    return -1;
                }
                else if (c1.getY() > c2.getY())
                {
                    return 1;
                }

                return 0;
            }
        });

        return possibleCoordinates;
    }

    // Returns Random Coordinate from All Hidden Coordinates
    private Coordinate targetRandomEasy()
    {
        List<Coordinate> possibleCoordinates = allHiddenCoordinates();

        int index = random.nextInt(possibleCoordinates.size());
        return possibleCoordinates.get(index);
    }

    // Returns Random Coordinate from Coordinates that could Possibly hold a Ship
    private Coordinate targetRandomMedium()
    {
        List<Coordinate> possibleCoordinates = allPossibleShipCoordinates();

        int index = random.nextInt(possibleCoordinates.size());
        return possibleCoordinates.get(index);
    }

    // Returns Coordinate that is most likely to be holding a Ship
    private Coordinate targetRandomHard()
    {
        List<Coordinate> possibleCoordinates = allPossibleShipCoordinates();
        List<Coordinate> mostLikelyCoordinates = new ArrayList<>();

        int mostLikelyAmount = 0;
        int lastAmount = 0;
        Coordinate lastCoordinate = new Coordinate(-1, -1);

        for (Coordinate coordinate : possibleCoordinates)
        {
            if (coordinate.equals(lastCoordinate))
            {
                lastAmount += 1;
            }
            else
            {
                lastAmount = 1;
                lastCoordinate = coordinate;
            }

            if (lastAmount > mostLikelyAmount)
            {
                mostLikelyAmount = lastAmount;
                mostLikelyCoordinates.clear();
                mostLikelyCoordinates.add(lastCoordinate);
            }
            else if (lastAmount == mostLikelyAmount)
            {
                mostLikelyCoordinates.add(lastCoordinate);
            }
        }

        int index = random.nextInt(mostLikelyCoordinates.size());
        return mostLikelyCoordinates.get(index);
    }

    // Returns Coordinate List of all Hit Ships
    private List<Coordinate> allKnownHitCoordinates()
    {
        List<Coordinate> knownHitCoordinates = new ArrayList<>();

        for (int i = 0; i < BoardSize.COLUMNS; i++)
        {
            for (int j = 0; j < BoardSize.ROWS; j++)
            {
                if (board.getStatus(i, j) == BoardStatus.HIT)
                {
                    knownHitCoordinates.add(new Coordinate(i, j));
                }
            }
        }

        return knownHitCoordinates;
    }

    // Returns True if Square is Holding a Ship that has been Hit
    private boolean squareIsHoldingHitShip(Coordinate coordinate)
    {
        int x = coordinate.getX();
        int y = coordinate.getY();

        if (board.getStatus(x, y) == BoardStatus.HIT)
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    // Returns True if there are multiple Hit Ships directly beside each other
    private boolean multipleHitsTogetherAnywhere()
    {
        for (int i = 0; i < BoardSize.COLUMNS; i++)
        {
            for (int j = 0; j < BoardSize.ROWS; j++)
            {
                Coordinate coordinate = new Coordinate(i, j);

                if (squareIsHoldingHitShip(coordinate))
                {
                    if (i > 0)
                    {
                        if (squareIsHoldingHitShip(new Coordinate(i - 1, j)))
                        {
                            return true;
                        }
                    }

                    if (j > 0)
                    {
                        if (squareIsHoldingHitShip(new Coordinate(i, j - 1)))
                        {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    // Returns Coordinate to a random direction beside a Hit Ship
    private Coordinate targetShipDumb()
    {
        List<Coordinate> knownHitCoordinates = allKnownHitCoordinates();
        List<Coordinate> logicalCoordinates = new ArrayList<>();

        for (Coordinate coordinate : knownHitCoordinates)
        {
            int x = coordinate.getX();
            int y = coordinate.getY();

            if (x > 0)
            {
                if (board.isStatusHidden(x - 1, y))
                {
                    logicalCoordinates.add(new Coordinate(x - 1, y));
                }
            }

            if (x < 9)
            {
                if (board.isStatusHidden(x + 1, y))
                {
                    logicalCoordinates.add(new Coordinate(x + 1, y));
                }
            }

            if (y > 0)
            {
                if (board.isStatusHidden(x, y - 1))
                {
                    logicalCoordinates.add(new Coordinate(x, y - 1));
                }
            }

            if (y < 9)
            {
                if (board.isStatusHidden(x, y + 1))
                {
                    logicalCoordinates.add(new Coordinate(x, y + 1));
                }
            }
        }

        int index = random.nextInt(logicalCoordinates.size());
        return logicalCoordinates.get(index);
    }

    // Returns Coordinate inline with 2 or more Hit Ships
    private Coordinate targetShipSmart()
    {
        List<Coordinate> knownHitCoordinates = allKnownHitCoordinates();
        List<Coordinate> logicalCoordinates = new ArrayList<>();

        for (Coordinate coordinate : knownHitCoordinates)
        {
            int x = coordinate.getX();
            int y = coordinate.getY();

            if (x > 1)
            {
                if (squareIsHoldingHitShip(new Coordinate(x - 1, y)))
                {
                    if (board.isStatusHidden(x - 2, y))
                    {
                        logicalCoordinates.add(new Coordinate(x - 2, y));
                    }
                }
            }

            if (x < 8)
            {
                if (squareIsHoldingHitShip(new Coordinate(x + 1, y)))
                {
                    if (board.isStatusHidden(x + 2, y))
                    {
                        logicalCoordinates.add(new Coordinate(x + 2, y));
                    }
                }
            }

            if (y > 1)
            {
                if (squareIsHoldingHitShip(new Coordinate(x, y - 1)))
                {
                    if (board.isStatusHidden(x, y - 2))
                    {
                        logicalCoordinates.add(new Coordinate(x, y - 2));
                    }
                }
            }

            if (y < 8)
            {
                if (squareIsHoldingHitShip(new Coordinate(x, y + 1)))
                {
                    if (board.isStatusHidden(x, y + 2))
                    {
                        logicalCoordinates.add(new Coordinate(x, y + 2));
                    }
                }
            }
        }

        if (logicalCoordinates.size() > 0)
        {
            int index = random.nextInt(logicalCoordinates.size());
            return logicalCoordinates.get(index);
        }
        else
        {
            return targetShipDumb();
        }
    }

    // Makes AI shoot
    public Coordinate shoot()
    {
        if (knowsAnyShipLocation())
        {
            if (multipleHitsTogetherAnywhere())
            {
                return targetShipSmart();
            }
            else
            {
                return targetShipDumb();
            }
        }
        else
        {
            if (difficulty == AIDifficulty.EASY)
            {
                return targetRandomEasy();
            }
            else if (difficulty == AIDifficulty.MEDIUM)
            {
                return targetRandomMedium();
            }
            else
            {
                return targetRandomHard();
            }
        }
    }
}


enum AIDifficulty
{
    EASY, MEDIUM, HARD
}


class Probability
{
    private Coordinate coordinate;
    private int probability;

    public Probability(Coordinate coordinate, int probability)
    {
        this.coordinate = coordinate;
        this.probability = probability;
    }

    public int getProbability()
    {
        return probability;
    }

    public void setProbabilty(int p)
    {
        probability = p;
    }

    public Coordinate getCoordinate()
    {
        return coordinate;
    }

    public void setCoordinate(Coordinate c)
    {
        coordinate = c;
    }
}