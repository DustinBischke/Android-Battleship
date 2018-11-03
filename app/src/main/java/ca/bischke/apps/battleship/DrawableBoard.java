package ca.bischke.apps.battleship;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.support.v7.widget.AppCompatImageButton;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;

import java.util.List;

@SuppressLint({"ClickableViewAccessibility", "ViewConstructor"})

public class DrawableBoard extends TableLayout
{
    protected DrawableSquare[][] squares;

    public DrawableBoard(Context context, int buttonWidth)
    {
        super(context);
        this.squares = new DrawableSquare[BoardSize.COLUMNS][BoardSize.ROWS];

        // Creates Grid of Squares
        for (int i = 0; i < BoardSize.ROWS; i++)
        {
            TableRow row = new TableRow(context);
            addView(row);

            for (int j = 0; j < BoardSize.COLUMNS; j++)
            {
                LinearLayout layout = new LinearLayout(context);
                LinearLayout.LayoutParams buttonLayoutParams = new LinearLayout.LayoutParams(buttonWidth, buttonWidth);

                // Creates Square with X = j, Y = i
                final DrawableSquare square = new DrawableSquare(context, new Coordinate(j, i));
                square.setLayoutParams(buttonLayoutParams);

                squares[j][i] = square;
                layout.addView(squares[j][i]);
                row.addView(layout);
            }
        }
    }

    // TODO Improve Efficiency
    public void colorReset()
    {
        for (int i = 0; i < BoardSize.COLUMNS; i++)
        {
            for (int j = 0; j < BoardSize.ROWS; j++)
            {
                squares[i][j].setColor(R.color.colorBoard);
            }
        }
    }

    public void colorCrosshair(int x, int y)
    {
        colorReset();

        for (int i = 0; i < BoardSize.COLUMNS; i++)
        {
            squares[i][y].setColor(R.color.colorTargetOuter);
        }

        for (int i = 0; i < BoardSize.ROWS; i++)
        {
            squares[x][i].setColor(R.color.colorTargetOuter);
        }

        squares[x][y].setColor(R.color.colorTargetInner);
    }
}


@SuppressLint({"ClickableViewAccessibility", "ViewConstructor"})

class DrawableBoardPlacing extends DrawableBoard
{
    private Board board;
    private List<Ship> ships;
    private Ship activeShip;

    private boolean shipFirstTouch;
    private boolean shipDragged;

    public DrawableBoardPlacing(Context context, Board board, int buttonSize)
    {
        super(context, buttonSize);
        this.board = board;
        this.ships = board.getShips();

        // Creates Grid of Squares
        for (int i = 0; i < BoardSize.ROWS; i++)
        {
            for (int j = 0; j < BoardSize.COLUMNS; j++)
            {
                final DrawableSquare square = squares[i][j];

                // Drag and Drop Event Handlers
                square.setOnTouchListener(new OnTouchListener()
                {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent)
                    {
                        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN)
                        {
                            int x = square.getCoordinate().getX();
                            int y = square.getCoordinate().getY();
                            boolean shipClicked = false;

                            for (Ship ship : ships)
                            {
                                for (Coordinate coordinate : ship.getListCoordinates())
                                {
                                    int shipX = coordinate.getX();
                                    int shipY = coordinate.getY();

                                    if (x == shipX && y == shipY)
                                    {
                                        if (activeShip == ship)
                                        {
                                            shipFirstTouch = false;
                                        }
                                        else
                                        {
                                            shipFirstTouch = true;
                                            activeShip = ship;
                                        }

                                        shipClicked = true;
                                        break;
                                    }
                                }
                            }

                            if (shipClicked)
                            {
                                colorShips();

                                ClipData data = ClipData.newPlainText("", "");
                                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(new LinearLayout(getContext()));
                                view.startDrag(data, shadowBuilder, view, 0);

                                return true;
                            }
                            else
                            {
                                activeShip = null;
                                colorShips();
                            }
                        }

                        return false;
                    }
                });

                square.setOnDragListener(new OnDragListener()
                {
                    @Override
                    public boolean onDrag(View view, DragEvent dragEvent)
                    {
                        if (activeShip != null)
                        {
                            switch(dragEvent.getAction())
                            {
                                case DragEvent.ACTION_DRAG_STARTED:
                                    shipDragged = false;
                                    break;
                                case DragEvent.ACTION_DRAG_ENTERED:
                                    DrawableSquare square = (DrawableSquare) view;
                                    Coordinate squareCoordinate = square.getCoordinate();

                                    if (shipDragged)
                                    {
                                        if (activeShip.getDirection() == ShipDirection.HORIZONTAL)
                                        {
                                            activeShip.setCoordinate(new Coordinate(squareCoordinate.getX() - ((activeShip.getLength() - 1) / 2), squareCoordinate.getY()));
                                        }
                                        else if (activeShip.getDirection() == ShipDirection.VERTICAL)
                                        {
                                            activeShip.setCoordinate(new Coordinate(squareCoordinate.getX(), squareCoordinate.getY() - ((activeShip.getLength() - 1) / 2)));
                                        }

                                        colorShips();
                                    }

                                    break;
                                case DragEvent.ACTION_DRAG_EXITED:
                                    shipDragged = true;
                                    break;
                                case DragEvent.ACTION_DROP:
                                    if (!shipDragged && !shipFirstTouch)
                                    {
                                        // Rotates Ships only if Center Square is Clicked
                                        /*DrawableSquare squareDrop = (DrawableSquare) view;

                                        if (squareDrop.getCoordinate().equals(activeShip.getCenter()))
                                        {
                                            activeShip.rotate();
                                            colorShips();
                                        }*/

                                        activeShip.rotate();
                                        colorShips();
                                    }
                                    break;
                                case DragEvent.ACTION_DRAG_ENDED:
                                    break;
                                default:
                                    break;
                            }
                        }

                        return true;
                    }
                });
            }
        }

        colorShips();
    }

    public Ship getActiveShip()
    {
        return activeShip;
    }

    public void setNoActiveShip()
    {
        activeShip = null;
    }

    public DrawableSquare getSquareFromCoordinate(Coordinate coordinate)
    {
        int x = coordinate.getX();
        int y = coordinate.getY();

        if (x >= 0 && x < BoardSize.COLUMNS && y >= 0 && y < BoardSize.ROWS)
        {
            return squares[x][y];
        }
        else
        {
            return squares[0][0];
        }
    }

    public void rotateIconReset()
    {
        for (int i = 0; i < BoardSize.COLUMNS; i++)
        {
            for (int j = 0; j < BoardSize.ROWS; j++)
            {
                squares[i][j].setImage(0);
            }
        }
    }

    // TODO Improve Efficiency - Reset Colors and Icons in a single function
    public void colorShips()
    {
        colorReset();
        rotateIconReset();

        for (Ship ship : ships)
        {
            for (Coordinate coordinate : ship.getListCoordinates())
            {
                if (board.isCollidingWithAny(ship))
                {
                    getSquareFromCoordinate(coordinate).setColor(R.color.colorCollision);
                }
                else if (ship == activeShip)
                {
                    if (coordinate.equals(ship.getCenter()))
                    {
                        getSquareFromCoordinate(coordinate).setImage(R.drawable.rotate);
                    }

                    getSquareFromCoordinate(coordinate).setColor(R.color.colorAccent);
                }
                else
                {
                    getSquareFromCoordinate(coordinate).setColor(R.color.colorShip);
                }
            }
        }
    }
}


@SuppressLint("ViewConstructor")

class DrawableSquare extends AppCompatImageButton
{
    private Coordinate coordinate;
    boolean clicked = false;

    DrawableSquare(Context context, Coordinate coordinate)
    {
        super(context);
        this.coordinate = coordinate;
        setColor(R.color.colorBoard);
        setPadding(0, 0, 0, 0);
        setScaleType(ScaleType.FIT_CENTER);
    }

    public Coordinate getCoordinate()
    {
        return coordinate;
    }

    public boolean isClicked()
    {
        return clicked;
    }

    public void setClicked(boolean clicked)
    {
        this.clicked = clicked;
    }

    public void setColor(int color)
    {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setStroke(4, getResources().getColor(R.color.colorStroke));
        drawable.setColor(getResources().getColor(color));
        setBackgroundDrawable(drawable);
    }

    public void setImage(int image)
    {
        setImageResource(image);
    }
}