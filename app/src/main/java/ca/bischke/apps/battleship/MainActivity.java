package ca.bischke.apps.battleship;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.Game;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesCallbackStatusCodes;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.GamesClientStatusCodes;
import com.google.android.gms.games.InvitationsClient;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.games.RealTimeMultiplayerClient;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.InvitationCallback;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.OnRealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateCallback;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "Battleship";

    // Request codes for the UIs that we show with startActivityForResult:
    final static int RC_SELECT_PLAYERS = 10000;
    final static int RC_INVITATION_INBOX = 10001;
    final static int RC_WAITING_ROOM = 10002;

    // Request code used to invoke sign in user interactions.
    private static final int RC_SIGN_IN = 9001;

    // Client used to sign in with Google APIs
    private GoogleSignInClient mGoogleSignInClient = null;

    // Client used to interact with the real time multiplayer system.
    private RealTimeMultiplayerClient mRealTimeMultiplayerClient = null;

    // Client used to interact with the Invitation system.
    private InvitationsClient mInvitationsClient = null;

    // Room ID where the currently active game is taking place; null if we're
    // not playing.
    String mRoomId = null;

    // Holds the configuration of the current room.
    RoomConfig mRoomConfig;

    // Are we playing in multiplayer mode?
    boolean mMultiplayer = false;

    // The participants in the currently active game
    ArrayList<Participant> mParticipants = null;

    // My participant ID in the currently active game
    String mMyId = null;

    // If non-null, this is the id of the invitation we received via the
    // invitation listener
    String mIncomingInvitationId = null;

    private AIDifficulty singlePlayerDifficulty = AIDifficulty.HARD;
    private GameMode gameMode = GameMode.CLASSIC;


    /**
     * Activity Lifecycle
     */

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        //Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        //setTheme(R.style.AppTheme_Dark);
        setContentView(R.layout.activity_main);
        hideSystemUI();

        mGoogleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);

        // Sets Sign In Button Click Event - onClick() in XML was not Working
        setSignInClickListener();

        switchToMainScreen();
    }

    @Override
    protected void onResume()
    {
        //Log.d(TAG, "onResume()");
        super.onResume();

        signInSilently();
    }

    @Override
    protected void onPause()
    {
        //Log.d(TAG, "onPause()");
        super.onPause();

        if (mInvitationsClient != null)
        {
            mInvitationsClient.unregisterInvitationCallback(mInvitationCallback);
        }
    }


    /**
     * Fullscreen Mode
     */

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus)
        {
            hideSystemUI();
        }
    }

    private void hideSystemUI()
    {
        if (Build.VERSION.SDK_INT >= 19)
        {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }


    /**
     * User Interface
     */

    private int currentScreen = -1;

    private static final int[] SCREENS = { R.id.screen_main_offline,
            R.id.screen_main,
            R.id.screen_please_wait,
            R.id.screen_place_ships,
            R.id.screen_target_board,
            R.id.screen_my_board };

    private static final int[] POPUPS = { R.id.popup_set_difficulty,
            R.id.popup_end_game,
            R.id.popup_quit_game };

    private void switchToScreen(int screen)
    {
        currentScreen = screen;

        for (int s : SCREENS)
        {
            if (s == screen)
            {
                findViewById(s).setVisibility(View.VISIBLE);
            }
            else
            {
                findViewById(s).setVisibility(View.GONE);
            }
        }

        boolean showInvitePopup = false;

        if (mIncomingInvitationId == null)
        {
            showInvitePopup = false;
        }
        else if (mMultiplayer)
        {
            if (currentScreen == R.id.screen_main)
            {
                showInvitePopup = true;
            }
        }
        else
        {
            if (currentScreen == R.id.screen_main || currentScreen == R.id.screen_place_ships
                    || currentScreen == R.id.screen_target_board || currentScreen == R.id.screen_my_board)
            {
                showInvitePopup = true;
            }
        }

        if (showInvitePopup)
        {
            findViewById(R.id.popup_invite).setVisibility(View.VISIBLE);
        }
        else
        {
            findViewById(R.id.popup_invite).setVisibility(View.GONE);
        }

        // Display Quit Button
        if (currentScreen == R.id.screen_place_ships || currentScreen == R.id.screen_target_board
                || currentScreen == R.id.screen_my_board)
        {
            findViewById(R.id.layout_quit_game).setVisibility(View.VISIBLE);
        }
        else
        {
            findViewById(R.id.layout_quit_game).setVisibility(View.GONE);
        }
    }

    private void switchToMainScreen()
    {
        if (mRealTimeMultiplayerClient != null)
        {
            switchToScreen(R.id.screen_main);
        }
        else
        {
            switchToScreen(R.id.screen_main_offline);
        }
    }

    private void showEndGamePopup(boolean win)
    {
        findViewById(R.id.popup_end_game).setVisibility(View.VISIBLE);

        if (mMultiplayer)
        {
            findViewById(R.id.button_play_again).setVisibility(View.GONE);

            if (win)
            {
                findViewById(R.id.image_trophy).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.text_winner)).setText(getString(R.string.you_win));
            }
            else
            {
                findViewById(R.id.image_trophy).setVisibility(View.GONE);
                ((TextView) findViewById(R.id.text_winner)).setText(getString(R.string.other_player_won, getOpponent().getDisplayName()));
            }
        }
        else
        {
            findViewById(R.id.button_play_again).setVisibility(View.VISIBLE);

            if (win)
            {
                findViewById(R.id.image_trophy).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.text_winner)).setText(getString(R.string.you_win));
            }
            else
            {
                findViewById(R.id.image_trophy).setVisibility(View.GONE);
                ((TextView) findViewById(R.id.text_winner)).setText(getString(R.string.you_lose));
            }
        }
    }

    private void displayPopup(int popup)
    {
        findViewById(popup).setVisibility(View.VISIBLE);
    }

    private void hidePopups()
    {
        for (int p : POPUPS)
        {
            findViewById(p).setVisibility(View.GONE);
        }
    }

    private void resetShipIcons()
    {
        ((ImageView)findViewById(R.id.image_my_carrier)).setImageResource(R.drawable.carrier);
        ((ImageView)findViewById(R.id.image_my_battleship)).setImageResource(R.drawable.battleship);
        ((ImageView)findViewById(R.id.image_my_cruiser)).setImageResource(R.drawable.cruiser);
        ((ImageView)findViewById(R.id.image_my_submarine)).setImageResource(R.drawable.submarine);
        ((ImageView)findViewById(R.id.image_my_destroyer)).setImageResource(R.drawable.destroyer);

        ((ImageView)findViewById(R.id.image_target_carrier)).setImageResource(R.drawable.carrier);
        ((ImageView)findViewById(R.id.image_target_battleship)).setImageResource(R.drawable.battleship);
        ((ImageView)findViewById(R.id.image_target_cruiser)).setImageResource(R.drawable.cruiser);
        ((ImageView)findViewById(R.id.image_target_submarine)).setImageResource(R.drawable.submarine);
        ((ImageView)findViewById(R.id.image_target_destroyer)).setImageResource(R.drawable.destroyer);
    }


    /**
     * Button Click Events
     */

    private void setSignInClickListener()
    {
        findViewById(R.id.button_sign_in).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                //Log.d(TAG, "Sign-In Button Clicked");
                startSignInIntent();
            }
        });
    }

    public void buttonSinglePlayer(View view)
    {
        //Log.d(TAG, "SinglePlayer Button Clicked");
        displayPopup(R.id.popup_set_difficulty);
    }

    public void buttonEasy(View view)
    {
        //Log.d(TAG, "Easy Button Clicked");
        hidePopups();
        singlePlayerDifficulty = AIDifficulty.EASY;
        startGameSinglePlayer();
    }

    public void buttonMedium(View view)
    {
        //Log.d(TAG, "Medium Button Clicked");
        hidePopups();
        singlePlayerDifficulty = AIDifficulty.MEDIUM;
        startGameSinglePlayer();
    }

    public void buttonHard(View view)
    {
        //Log.d(TAG, "Hard Button Clicked");
        hidePopups();
        singlePlayerDifficulty = AIDifficulty.HARD;
        startGameSinglePlayer();
    }

    public void buttonClassic(View view)
    {
        gameMode = GameMode.CLASSIC;
        ((TextView)findViewById(R.id.text_gamemode_description)).setText(getString(R.string.classic_description));
    }

    public void buttonHitStreak(View view)
    {
        gameMode = GameMode.HITSTREAK;
        ((TextView)findViewById(R.id.text_gamemode_description)).setText(getString(R.string.hitstreak_description));
    }

    public void buttonSignOut(View view)
    {
        //Log.d(TAG, "Sign-Out Button Clicked");
        signOut();
        switchToScreen(R.id.screen_main_offline);
    }

    public void buttonExitGame(View view)
    {
        //Log.d(TAG, "Exit Game Button Clicked");
        if(Build.VERSION.SDK_INT >= 21)
        {
            finishAndRemoveTask();
        }
        else
        {
            finish();
        }

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    public void buttonQuickMatch(View view)
    {
        //Log.d(TAG, "Quick Game Button Clicked");
        startQuickGame();
    }

    public void buttonInviteFriends(View view)
    {
        //Log.d(TAG, "Invite Friends Button Clicked");
        switchToScreen(R.id.screen_please_wait);

        mRealTimeMultiplayerClient.getSelectOpponentsIntent(1, 1).addOnSuccessListener(new OnSuccessListener<Intent>()
        {
            @Override
            public void onSuccess(Intent intent)
            {
                startActivityForResult(intent, RC_SELECT_PLAYERS);
            }
        }).addOnFailureListener(createFailureListener("There was a problem selecting opponents."));
    }

    public void buttonInvitations(View view)
    {
        //Log.d(TAG, "Invitations Button Clicked");
        switchToScreen(R.id.screen_please_wait);

        // Shows Received Invitations
        mInvitationsClient.getInvitationInboxIntent().addOnSuccessListener(new OnSuccessListener<Intent>()
        {
            @Override
            public void onSuccess(Intent intent)
            {
                startActivityForResult(intent, RC_INVITATION_INBOX);
            }
        }).addOnFailureListener(createFailureListener("There was a problem getting the inbox."));
    }

    public void buttonAcceptInvitePopup(View view)
    {
        //Log.d(TAG, "Accept Invite Popup Button Clicked");
        acceptInviteToRoom(mIncomingInvitationId);
        mIncomingInvitationId = null;
    }

    public void buttonDenyInvitePopup(View view)
    {
        //Log.d(TAG, "Deny Invite Popup Button Clicked");
        findViewById(R.id.popup_invite).setVisibility(View.GONE);
    }

    public void buttonRotateShip(View view)
    {
        //Log.d(TAG, "Rotate Ship Button Clicked");
        if (myDrawableBoardPlacing.getActiveShip() != null)
        {
            myDrawableBoardPlacing.getActiveShip().rotate();
            myDrawableBoardPlacing.colorShips();
        }
    }

    public void buttonPlaceShipsRandomly(View view)
    {
        //Log.d(TAG, "Place Ships Randomly Button Clicked");
        myBoard.placeShipsRandom();
        myDrawableBoardPlacing.setNoActiveShip();
        myDrawableBoardPlacing.colorShips();
    }

    public void buttonConfirmShips(View view)
    {
        //Log.d(TAG, "Confirm Ships Button Clicked");

        if (myBoard.isValidBoard())
        {
            myBoard.confirmShipLocations();
            switchToScreen(R.id.screen_target_board);
            displayDrawableBoards();
            ai = new AI(singlePlayerDifficulty, myBoard);
        }
        else
        {
            displayError(getString(R.string.invalid_ships_error));
        }
    }

    public void buttonPlayAgain(View view)
    {
        //Log.d(TAG, "Play Again Button Clicked");
        hidePopups();
        startGameSinglePlayer();
    }

    public void buttonReturnToMenu(View view)
    {
        //Log.d(TAG, "Return To Menu Button Clicked");
        hidePopups();

        if (mMultiplayer)
        {
            leaveRoom();
        }

        switchToMainScreen();
    }

    public void buttonResume(View view)
    {
        //Log.d(TAG, "Resume Button Clicked");
        hidePopups();
    }

    public void buttonQuitGame(View view)
    {
        //Log.d(TAG, "Quit Game Clicked");
        confirmQuit();
    }

    public void clickOutsidePopup(View view)
    {
        hidePopups();
    }

    public void clickInPopup(View view)
    {
        //
    }


    /**
     * Android UI / Hardware Back Button
     */

    @Override
    public void onBackPressed()
    {
        if (currentScreen != R.id.screen_main_offline && currentScreen != R.id.screen_main)
        {
            confirmQuit();
        }
    }


    /**
     * Alert Dialogs
     */

    private void confirmQuit()
    {
        findViewById(R.id.popup_quit_game).setVisibility(View.VISIBLE);
    }

    private void displayError(String error)
    {
        final AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AlertDialogCustom));
        builder.setTitle(R.string.error_title);
        builder.setMessage(error);

        builder.setNeutralButton(R.string.ok, null);

        builder.show();
    }


    /**
     * Handler and Runnables
     */

    private final Handler handler = new Handler();

    private final Runnable timer = new Runnable()
    {
        @Override
        public void run()
        {
            if (timeRemaining < 0)
            {
                handler.removeCallbacks(this);
                return;
            }

            if (!myBoard.areShipsPlaced())
            {
                timerTick();
            }

            timeRemaining -= 1;
            handler.postDelayed(this, 1000);
        }
    };

    final Runnable aiTargetDelay = new Runnable()
    {
        @Override
        public void run()
        {
            Coordinate coordinate = ai.shoot();
            int x = coordinate.getX();
            int y = coordinate.getY();

            myDrawableBoard.colorCrosshair(x, y);

            if (myBoard.getStatus(x, y) == BoardStatus.HIDDEN_SHIP)
            {
                myBoard.setStatus(x, y, BoardStatus.HIT);
                myDrawableBoard.squares[x][y].setImage(R.drawable.hit);

                displaySunkShip(myBoard, myDrawableBoard);

                if (myBoard.allShipsSunk())
                {
                    handler.removeCallbacksAndMessages(this);
                    gameInProgress = false;
                    showEndGamePopup(false);
                }
                else
                {
                    if (gameMode == GameMode.CLASSIC)
                    {
                        handler.removeCallbacksAndMessages(this);
                        myTurn = true;
                        canTarget = true;
                        handler.postDelayed(delayTransition, 1000);
                    }
                    else if (gameMode == GameMode.HITSTREAK)
                    {
                        handler.postDelayed(this, 1000);
                    }
                }
            }
            else
            {
                handler.removeCallbacksAndMessages(this);
                myTurn = true;
                canTarget = true;
                myBoard.setStatus(x, y, BoardStatus.MISS);
                myDrawableBoard.squares[x][y].setImage(R.drawable.miss);
                handler.postDelayed(delayTransition, 1000);
            }
        }
    };

    final Runnable delayTransition = new Runnable()
    {
        @Override
        public void run()
        {
            if (myTurn)
            {
                switchToScreen(R.id.screen_target_board);
                myDrawableBoard.colorReset();
            }
            else
            {
                switchToScreen(R.id.screen_my_board);
                targetDrawableBoard.colorReset();
            }
        }
    };


    /**
     * Game Logic
     */

    private AI ai;
    private Board myBoard;
    private Board aiBoard;
    private DrawableBoardPlacing myDrawableBoardPlacing;
    private DrawableBoard myDrawableBoard;
    private DrawableBoard targetDrawableBoard;
    private boolean gameInProgress = true;
    private boolean myTurn = false;
    private boolean canTarget = false;
    private int timeRemaining = -1;
    private int placeShipsTime = 20;
    private int shipsSunk = 0;

    private void displayDrawableBoardPlacing()
    {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int displayWidth = displayMetrics.widthPixels;
        int buttonSize = displayWidth / (BoardSize.COLUMNS + 1);

        myDrawableBoardPlacing = new DrawableBoardPlacing(this, myBoard, buttonSize);

        LinearLayout BattleshipGridPlacing = findViewById(R.id.battleship_grid_placing);
        BattleshipGridPlacing.removeAllViewsInLayout();
        BattleshipGridPlacing.addView(myDrawableBoardPlacing);
    }

    private void displayDrawableBoards()
    {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int displayWidth = displayMetrics.widthPixels;
        int buttonSize = displayWidth / (BoardSize.COLUMNS + 1);

        // Targeting Board
        targetDrawableBoard = new DrawableBoard(this, buttonSize);

        for (int i = 0; i < BoardSize.ROWS; i++)
        {
            for (int j = 0; j < BoardSize.COLUMNS; j++)
            {
                final DrawableSquare square = targetDrawableBoard.squares[i][j];

                // Drag and Drop Event Handlers
                square.setOnTouchListener(new View.OnTouchListener()
                {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent)
                    {
                        if (gameInProgress && myTurn && canTarget)
                        {
                            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN)
                            {
                                ClipData data = ClipData.newPlainText("", "");
                                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(new LinearLayout(targetDrawableBoard.getContext()));
                                view.startDrag(data, shadowBuilder, view, 0);

                                return true;
                            }
                        }

                        return false;
                    }
                });

                square.setOnDragListener(new View.OnDragListener()
                {
                    @Override
                    public boolean onDrag(View view, DragEvent dragEvent)
                    {
                        switch(dragEvent.getAction())
                        {
                            case DragEvent.ACTION_DRAG_STARTED:
                                break;
                            case DragEvent.ACTION_DRAG_ENTERED:
                                DrawableSquare squareEnter = (DrawableSquare) view;
                                Coordinate squareEnterCoordinate = squareEnter.getCoordinate();
                                targetDrawableBoard.colorCrosshair(squareEnterCoordinate.getX(), squareEnterCoordinate.getY());
                                break;
                            case DragEvent.ACTION_DRAG_EXITED:
                                targetDrawableBoard.colorReset();
                                break;
                            case DragEvent.ACTION_DROP:
                                DrawableSquare square = (DrawableSquare) view;

                                if (!square.isClicked())
                                {
                                    targetCoordinate(square);
                                }

                                break;
                            case DragEvent.ACTION_DRAG_ENDED:
                                break;
                            default:
                                break;
                        }

                        return true;
                    }
                });
            }
        }

        LinearLayout BattleshipGrid = findViewById(R.id.target_battleship_grid);
        BattleshipGrid.removeAllViewsInLayout();
        BattleshipGrid.addView(targetDrawableBoard);

        // My Board
        myDrawableBoard = new DrawableBoard(this, buttonSize);
        LinearLayout BattleshipGrid2 = findViewById(R.id.my_battleship_grid);
        BattleshipGrid2.removeAllViewsInLayout();
        BattleshipGrid2.addView(myDrawableBoard);
    }

    private void startGameSinglePlayer()
    {
        resetShipIcons();
        mMultiplayer = false;
        gameInProgress = true;
        myTurn = true;
        canTarget = true;
        switchToScreen(R.id.screen_place_ships);
        findViewById(R.id.text_timer).setVisibility(View.GONE);
        ((TextView)findViewById(R.id.text_other_player_turn)).setText(getString(R.string.other_player_turn, getString(R.string.ai)));
        findViewById(R.id.button_confirm_ships).setVisibility(View.VISIBLE);

        myBoard = new Board();
        displayDrawableBoardPlacing();

        aiBoard = new Board();
        aiBoard.placeShipsRandom();
        aiBoard.confirmShipLocations();
    }

    private void startGameMultiPlayer()
    {
        resetShipIcons();
        gameMode = GameMode.CLASSIC;
        mMultiplayer = true;
        gameInProgress = true;
        myTurn = false;
        canTarget = false;
        shipsSunk = 0;
        timeRemaining = placeShipsTime;
        switchToScreen(R.id.screen_place_ships);
        findViewById(R.id.text_timer).setVisibility(View.VISIBLE);
        ((TextView)findViewById(R.id.text_other_player_turn)).setText(getString(R.string.other_player_turn, getOpponent().getDisplayName()));
        findViewById(R.id.button_confirm_ships).setVisibility(View.GONE);

        myBoard = new Board();
        displayDrawableBoardPlacing();

        handler.postDelayed(timer, 1000);

        determineStartingPlayer();
    }

    private void timerTick()
    {
        if (timeRemaining > 0)
        {
            ((TextView) findViewById(R.id.text_timer)).setText(String.valueOf(timeRemaining));
        }
        else if (timeRemaining == 0)
        {
            timeRemaining = -1;
            handler.removeCallbacks(timer);

            if (myBoard.isValidBoard())
            {
                myBoard.confirmShipLocations();
            }
            else
            {
                myBoard.placeShipsRandom();
                myBoard.confirmShipLocations();
            }

            myBoard.setShipsPlaced(true);

            handler.postDelayed(delayTransition, 0);

            displayDrawableBoards();
        }
    }

    private void targetCoordinate(DrawableSquare square)
    {
        canTarget = false;
        Coordinate coordinate = square.getCoordinate();
        int x = coordinate.getX();
        int y = coordinate.getY();

        targetDrawableBoard.colorCrosshair(x, y);

        if (mMultiplayer)
        {
            square.setClicked(true);
            sendTargetCoordinate(x, y);
        }
        else
        {
            square.setClicked(true);

            if (aiBoard.getStatus(x, y) == BoardStatus.HIDDEN_SHIP)
            {
                if (gameMode == GameMode.HITSTREAK)
                {
                    canTarget = true;
                }

                square.setImage(R.drawable.hit);
                aiBoard.setStatus(x, y, BoardStatus.HIT);

                displaySunkShip(aiBoard, targetDrawableBoard);

                if (aiBoard.allShipsSunk())
                {
                    gameInProgress = false;
                    showEndGamePopup(true);
                }
                else
                {
                    if (gameMode == GameMode.CLASSIC)
                    {
                        myTurn = false;
                        handler.postDelayed(delayTransition, 600);
                        handler.postDelayed(aiTargetDelay, 1500);
                    }
                }
            }
            else
            {
                myTurn = false;
                square.setImage(R.drawable.miss);
                aiBoard.setStatus(x, y, BoardStatus.MISS);

                handler.postDelayed(delayTransition, 600);
                handler.postDelayed(aiTargetDelay, 1500);
            }
        }
    }

    private void displaySunkShip(Board board, DrawableBoard dBoard)
    {
        if (board.shipToSink() != null)
        {
            Ship ship = board.shipToSink();

            for (Coordinate c : ship.getListCoordinates())
            {
                dBoard.squares[c.getX()][c.getY()].setImage(R.drawable.sunk);
            }

            if (board == myBoard)
            {
                switch(ship.getType())
                {
                    case CARRIER:
                        ((ImageView)findViewById(R.id.image_my_carrier)).setImageResource(R.drawable.carrier_sunk);
                        break;
                    case BATTLESHIP:
                        ((ImageView)findViewById(R.id.image_my_battleship)).setImageResource(R.drawable.battleship_sunk);
                        break;
                    case CRUISER:
                        ((ImageView)findViewById(R.id.image_my_cruiser)).setImageResource(R.drawable.cruiser_sunk);
                        break;
                    case SUBMARINE:
                        ((ImageView)findViewById(R.id.image_my_submarine)).setImageResource(R.drawable.submarine_sunk);
                        break;
                    case DESTROYER:
                        ((ImageView)findViewById(R.id.image_my_destroyer)).setImageResource(R.drawable.destroyer_sunk);
                        break;
                }
            }
            else if (board == aiBoard)
            {
                switch(ship.getType())
                {
                    case CARRIER:
                        ((ImageView)findViewById(R.id.image_target_carrier)).setImageResource(R.drawable.carrier_sunk);
                        break;
                    case BATTLESHIP:
                        ((ImageView)findViewById(R.id.image_target_battleship)).setImageResource(R.drawable.battleship_sunk);
                        break;
                    case CRUISER:
                        ((ImageView)findViewById(R.id.image_target_cruiser)).setImageResource(R.drawable.cruiser_sunk);
                        break;
                    case SUBMARINE:
                        ((ImageView)findViewById(R.id.image_target_submarine)).setImageResource(R.drawable.submarine_sunk);
                        break;
                    case DESTROYER:
                        ((ImageView)findViewById(R.id.image_target_destroyer)).setImageResource(R.drawable.destroyer_sunk);
                        break;
                }
            }

            board.sinkShips();
        }
    }

    private void determineStartingPlayer()
    {
        if (mMyId.compareTo(getOpponent().getParticipantId()) > 0)
        {
            myTurn = true;
            canTarget = true;
        }
    }


    /**
     * Multiplayer Communications
     */

    private OnRealTimeMessageReceivedListener mOnRealTimeMessageReceivedListener = new OnRealTimeMessageReceivedListener()
    {
        @Override
        public void onRealTimeMessageReceived(@NonNull RealTimeMessage realTimeMessage)
        {
            byte[] message = realTimeMessage.getMessageData();
            int messageType = message[0];

            // Received Target Location
            if (messageType == 0)
            {
                int x = message[1];
                int y = message[2];

                myDrawableBoard.colorCrosshair(x, y);

                // Hit
                if (myBoard.getStatus(x, y) == BoardStatus.HIDDEN_SHIP)
                {
                    myBoard.setStatus(x, y, BoardStatus.HIT);

                    // Sunk Ship
                    if (myBoard.shipToSink() != null)
                    {
                        Ship ship = myBoard.shipToSink();
                        displaySunkShip(myBoard, myDrawableBoard);
                        sendSinkShip(ship);

                        if (myBoard.allShipsSunk())
                        {
                            gameInProgress = false;
                            showEndGamePopup(false);
                        }
                    }
                    else
                    {
                        myDrawableBoard.squares[x][y].setImage(R.drawable.hit);
                        sendSquareStatus(x, y, 1);
                    }

                    if (gameMode == GameMode.CLASSIC)
                    {
                        myTurn = true;
                        canTarget = true;
                        handler.postDelayed(delayTransition, 1000);
                    }
                }
                // Miss
                else
                {
                    myBoard.setStatus(x, y, BoardStatus.MISS);
                    myDrawableBoard.squares[x][y].setImage(R.drawable.miss);
                    sendSquareStatus(x, y, 0);
                    myTurn = true;
                    canTarget = true;
                    handler.postDelayed(delayTransition, 1000);
                }
            }
            // Received Status of Targeted Location - Response to Target Location
            else if (messageType == 1)
            {
                int x = message[1];
                int y = message[2];
                int status = message[3];

                // If Hit, Set Square to Hit
                if (status == 1)
                {
                    targetDrawableBoard.squares[x][y].setImage(R.drawable.hit);

                    if (gameMode == GameMode.HITSTREAK)
                    {
                        canTarget = true;
                    }
                }
                // If Miss - Set Square to Miss and End turn
                else if (status == 0)
                {
                    targetDrawableBoard.squares[x][y].setImage(R.drawable.miss);
                    myTurn = false;
                    handler.postDelayed(delayTransition, 1000);
                }
            }
            // Received Sink Ship
            else if (messageType == 2)
            {
                int x = message[1];
                int y = message[2];
                int direction = message[3];
                int length = message[4];
                int type = message[5];

                if (direction == 0)
                {
                    for (int i = x; i < x + length; i++)
                    {
                        targetDrawableBoard.squares[i][y].setImage(R.drawable.sunk);
                    }
                }
                else if (direction == 1)
                {
                    for (int i = y; i < y + length; i++)
                    {
                        targetDrawableBoard.squares[x][i].setImage(R.drawable.sunk);
                    }
                }

                switch(type)
                {
                    case 1:
                        ((ImageView)findViewById(R.id.image_target_carrier)).setImageResource(R.drawable.carrier_sunk);
                        break;
                    case 2:
                        ((ImageView)findViewById(R.id.image_target_battleship)).setImageResource(R.drawable.battleship_sunk);
                        break;
                    case 3:
                        ((ImageView)findViewById(R.id.image_target_cruiser)).setImageResource(R.drawable.cruiser_sunk);
                        break;
                    case 4:
                        ((ImageView)findViewById(R.id.image_target_submarine)).setImageResource(R.drawable.submarine_sunk);
                        break;
                    case 5:
                        ((ImageView)findViewById(R.id.image_target_destroyer)).setImageResource(R.drawable.destroyer_sunk);
                        break;
                }

                shipsSunk += 1;

                if (shipsSunk == 5)
                {
                    gameInProgress = false;
                    showEndGamePopup(true);
                }
                else
                {
                    if (gameMode == GameMode.CLASSIC)
                    {
                        myTurn = false;
                        handler.postDelayed(delayTransition, 1000);
                    }
                    else if (gameMode == GameMode.HITSTREAK)
                    {
                        canTarget = true;
                    }
                }
            }
        }
    };



    private void sendReliableMessage(byte[] message)
    {
        for (Participant p : mParticipants)
        {
            if (p.getParticipantId().equals(mMyId))
            {
                continue;
            }
            if (p.getStatus() != Participant.STATUS_JOINED)
            {
                continue;
            }

            mRealTimeMultiplayerClient.sendReliableMessage(message, mRoomId, p.getParticipantId(), new RealTimeMultiplayerClient.ReliableMessageSentCallback()
            {
                @Override
                public void onRealTimeMessageSent(int i, int i1, String s) { }
            }).addOnCompleteListener(new OnCompleteListener<Integer>()
            {
                @Override
                public void onComplete(@NonNull Task<Integer> task) { }
            });
        }
    }

    private void sendTargetCoordinate(int x, int y)
    {
        byte[] message = new byte[3];
        message[0] = 0;
        message[1] = (byte) x;
        message[2] = (byte) y;

        sendReliableMessage(message);
    }

    private void sendSquareStatus(int x, int y, int status)
    {
        byte[] message = new byte[4];
        message[0] = 1;
        message[1] = (byte) x;
        message[2] = (byte) y;
        message[3] = (byte) status;

        sendReliableMessage(message);
    }

    private void sendSinkShip(Ship ship)
    {
        byte[] message = new byte[6];
        message[0] = 2;
        message[1] = (byte) ship.getCoordinate().getX();
        message[2] = (byte) ship.getCoordinate().getY();

        int direction = -1;

        if (ship.getDirection() == ShipDirection.HORIZONTAL)
        {
            direction = 0;
        }
        else if (ship.getDirection() == ShipDirection.VERTICAL)
        {
            direction = 1;
        }

        message[3] = (byte) direction;
        message[4] = (byte) ship.getType().getLength();
        message[5] = (byte) ship.getType().getId();

        sendReliableMessage(message);
    }


    /**
     * Google Play Games Services API
     * Code Samples provided by:
     * https://developers.google.com/games/services/android/quickstart
     * https://github.com/playgameservices/android-basic-samples
     */

    void startQuickGame()
    {
        final int MIN_OPPONENTS = 1;
        final int MAX_OPPONENTS = 1;

        Bundle autoMatchCriteria = RoomConfig.createAutoMatchCriteria(MIN_OPPONENTS, MAX_OPPONENTS, 0);
        switchToScreen(R.id.screen_please_wait);

        mRoomConfig = RoomConfig.builder(mRoomUpdateCallback)
                .setOnMessageReceivedListener(mOnRealTimeMessageReceivedListener)
                .setRoomStatusUpdateCallback(mRoomStatusUpdateCallback)
                .setAutoMatchCriteria(autoMatchCriteria)
                .build();
        mRealTimeMultiplayerClient.create(mRoomConfig);
    }

    public void startSignInIntent()
    {
        startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }

    public void signInSilently()
    {
        //Log.d(TAG, "signInSilently()");

        mGoogleSignInClient.silentSignIn().addOnCompleteListener(this, new OnCompleteListener<GoogleSignInAccount>()
        {
            @Override
            public void onComplete(@NonNull Task<GoogleSignInAccount> task)
            {
                if (task.isSuccessful())
                {
                    //Log.d(TAG, "signInSilently(): success");
                    onConnected(task.getResult());
                }
                else
                {
                    //Log.d(TAG, "signInSilently(): failure", task.getException());
                    onDisconnected();
                }
            }
        });
    }

    public void signOut()
    {
        //Log.d(TAG, "signOut()");

        mGoogleSignInClient.signOut().addOnCompleteListener(this, new OnCompleteListener<Void>()
        {
            @Override
            public void onComplete(@NonNull Task<Void> task)
            {
                if (task.isSuccessful())
                {
                    //Log.d(TAG, "signOut(): success");
                }
                else
                {
                    handleException(task.getException(), "signOut() failed!");
                }

                onDisconnected();
            }
        });
    }

    private void handleException(Exception exception, String details)
    {
        int status = 0;

        if (exception instanceof ApiException)
        {
            ApiException apiException = (ApiException) exception;
            status = apiException.getStatusCode();
        }

        String errorString = null;
        switch (status)
        {
            case GamesCallbackStatusCodes.OK:
                break;
            case GamesClientStatusCodes.MULTIPLAYER_ERROR_NOT_TRUSTED_TESTER:
                errorString = getString(R.string.status_multiplayer_error_not_trusted_tester);
                break;
            case GamesClientStatusCodes.MATCH_ERROR_ALREADY_REMATCHED:
                errorString = getString(R.string.match_error_already_rematched);
                break;
            case GamesClientStatusCodes.NETWORK_ERROR_OPERATION_FAILED:
                errorString = getString(R.string.network_error_operation_failed);
                break;
            case GamesClientStatusCodes.INTERNAL_ERROR:
                errorString = getString(R.string.internal_error);
                break;
            case GamesClientStatusCodes.MATCH_ERROR_INACTIVE_MATCH:
                errorString = getString(R.string.match_error_inactive_match);
                break;
            case GamesClientStatusCodes.MATCH_ERROR_LOCALLY_MODIFIED:
                errorString = getString(R.string.match_error_locally_modified);
                break;
            default:
                errorString = getString(R.string.unexpected_status, GamesClientStatusCodes.getStatusCodeString(status));
                break;
        }

        if (errorString == null)
        {
            return;
        }

        String message = getString(R.string.status_exception_error, details, status, exception);
        displayError(message + "\n" + errorString);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        if (requestCode == RC_SIGN_IN)
        {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(intent);

            try
            {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                onConnected(account);
            }
            catch (ApiException apiException)
            {
                String message = apiException.getMessage();

                if (message == null || message.isEmpty())
                {
                    message = getString(R.string.signin_other_error);
                }

                onDisconnected();

                displayError(message);
            }
        }
        else if (requestCode == RC_SELECT_PLAYERS)
        {
            // we got the result from the "select players" UI -- ready to create the room
            handleSelectPlayersResult(resultCode, intent);
        }
        else if (requestCode == RC_INVITATION_INBOX)
        {
            // we got the result from the "select invitation" UI (invitation inbox). We're
            // ready to accept the selected invitation:
            handleInvitationInboxResult(resultCode, intent);
        }
        else if (requestCode == RC_WAITING_ROOM)
        {
            // we got the result from the "waiting room" UI.
            if (resultCode == Activity.RESULT_OK)
            {
                // ready to start playing
                //Log.d(TAG, "Starting game (waiting room returned OK).");
                startGameMultiPlayer();
            }
            else if (resultCode == GamesActivityResultCodes.RESULT_LEFT_ROOM)
            {
                // player indicated that they want to leave the room
                leaveRoom();
            }
            else if (resultCode == Activity.RESULT_CANCELED)
            {
                // Dialog was cancelled (user pressed back key, for instance). In our game,
                // this means leaving the room too. In more elaborate games, this could mean
                // something else (like minimizing the waiting room UI).
                leaveRoom();
            }
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }

    // Handle the result of the "Select players UI" we launched when the user clicked the
    // "Invite friends" button. We react by creating a room with those players.

    private void handleSelectPlayersResult(int response, Intent data)
    {
        if (response != Activity.RESULT_OK)
        {
            //Log.w(TAG, "*** select players UI cancelled, " + response);
            switchToMainScreen();
            return;
        }

        //Log.d(TAG, "Select players UI succeeded.");

        // get the invitee list
        final ArrayList<String> invitees = data.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);
        //Log.d(TAG, "Invitee count: " + invitees.size());

        // get the automatch criteria
        Bundle autoMatchCriteria = null;
        int minAutoMatchPlayers = data.getIntExtra(Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
        int maxAutoMatchPlayers = data.getIntExtra(Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);

        if (minAutoMatchPlayers > 0 || maxAutoMatchPlayers > 0)
        {
            autoMatchCriteria = RoomConfig.createAutoMatchCriteria(minAutoMatchPlayers, maxAutoMatchPlayers, 0);
            //Log.d(TAG, "Automatch criteria: " + autoMatchCriteria);
        }

        // create the room
        //Log.d(TAG, "Creating room...");
        switchToScreen(R.id.screen_please_wait);

        mRoomConfig = RoomConfig.builder(mRoomUpdateCallback)
                .addPlayersToInvite(invitees)
                .setOnMessageReceivedListener(mOnRealTimeMessageReceivedListener)
                .setRoomStatusUpdateCallback(mRoomStatusUpdateCallback)
                .setAutoMatchCriteria(autoMatchCriteria).build();
        mRealTimeMultiplayerClient.create(mRoomConfig);

        //Log.d(TAG, "Room created, waiting for it to be ready...");
    }

    // Handle the result of the invitation inbox UI, where the player can pick an invitation
    // to accept. We react by accepting the selected invitation, if any.
    private void handleInvitationInboxResult(int response, Intent data)
    {
        if (response != Activity.RESULT_OK)
        {
            //Log.w(TAG, "*** invitation inbox UI cancelled, " + response);
            switchToMainScreen();
            return;
        }

        //Log.d(TAG, "Invitation inbox UI succeeded.");
        Invitation invitation = data.getExtras().getParcelable(Multiplayer.EXTRA_INVITATION);

        // accept invitation
        if (invitation != null)
        {
            acceptInviteToRoom(invitation.getInvitationId());
        }
    }

    // Accept the given invitation.
    void acceptInviteToRoom(String invitationId)
    {
        // accept the invitation
        //Log.d(TAG, "Accepting invitation: " + invitationId);

        mRoomConfig = RoomConfig.builder(mRoomUpdateCallback)
                .setInvitationIdToAccept(invitationId)
                .setOnMessageReceivedListener(mOnRealTimeMessageReceivedListener)
                .setRoomStatusUpdateCallback(mRoomStatusUpdateCallback)
                .build();

        switchToScreen(R.id.screen_please_wait);

        mRealTimeMultiplayerClient.join(mRoomConfig).addOnSuccessListener(new OnSuccessListener<Void>()
        {
            @Override
            public void onSuccess(Void aVoid)
            {
                //Log.d(TAG, "Room Joined Successfully!");
            }
        });
    }

    // Activity is going to the background. We have to leave the current room.
    @Override
    public void onStop()
    {
        //Log.d(TAG, "**** got onStop");

        // if we're in a room, leave it.
        leaveRoom();

        switchToMainScreen();

        super.onStop();
    }

    // Leave the room.
    void leaveRoom()
    {
        //Log.d(TAG, "Leaving room.");

        if (mRoomId != null)
        {
            mRealTimeMultiplayerClient.leave(mRoomConfig, mRoomId).addOnCompleteListener(new OnCompleteListener<Void>()
            {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    mRoomId = null;
                    mRoomConfig = null;
                }
            });

            switchToScreen(R.id.screen_please_wait);
        }
        else
        {
            switchToMainScreen();
        }
    }

    // Show the waiting room UI to track the progress of other players as they enter the
    // room and get connected.
    void showWaitingRoom(Room room)
    {
        // minimum number of players required for our game
        // For simplicity, we require everyone to join the game before we start it
        // (this is signaled by Integer.MAX_VALUE).
        final int MIN_PLAYERS = Integer.MAX_VALUE;
        mRealTimeMultiplayerClient.getWaitingRoomIntent(room, MIN_PLAYERS).addOnSuccessListener(new OnSuccessListener<Intent>()
        {
            @Override
            public void onSuccess(Intent intent) {
                // show waiting room UI
                startActivityForResult(intent, RC_WAITING_ROOM);
            }
        }).addOnFailureListener(createFailureListener("There was a problem getting the waiting room!"));
    }

    private InvitationCallback mInvitationCallback = new InvitationCallback()
    {
        // Called when we get an invitation to play a game. We react by showing that to the user.
        @Override
        public void onInvitationReceived(@NonNull Invitation invitation)
        {
            // We got an invitation to play a game! So, store it in
            // mIncomingInvitationId
            // and show the popup on the screen.
            mIncomingInvitationId = invitation.getInvitationId();
            ((TextView) findViewById(R.id.text_incoming_invitation)).setText(getString(R.string.challenged_you, invitation.getInviter().getDisplayName()));
            switchToScreen(currentScreen); // This will show the invitation popup
        }

        @Override
        public void onInvitationRemoved(@NonNull String invitationId)
        {
            if (mIncomingInvitationId.equals(invitationId) && mIncomingInvitationId != null)
            {
                mIncomingInvitationId = null;
                switchToScreen(currentScreen); // This will hide the invitation popup
            }
        }
    };

    /**
     * CALLBACKS SECTION. This section shows how we implement the Games API callbacks.
     */

    private String mPlayerId;

    // The currently signed in account, used to check the account has changed outside of this activity when resuming.
    GoogleSignInAccount mSignedInAccount = null;

    private void onConnected(GoogleSignInAccount googleSignInAccount)
    {
        //Log.d(TAG, "onConnected(): connected to Google APIs");
        if (mSignedInAccount != googleSignInAccount)
        {
            mSignedInAccount = googleSignInAccount;

            // update the clients
            mRealTimeMultiplayerClient = Games.getRealTimeMultiplayerClient(this, googleSignInAccount);
            mInvitationsClient = Games.getInvitationsClient(MainActivity.this, googleSignInAccount);

            // get the playerId from the PlayersClient
            PlayersClient playersClient = Games.getPlayersClient(this, googleSignInAccount);
            playersClient.getCurrentPlayer().addOnSuccessListener(new OnSuccessListener<com.google.android.gms.games.Player>()
            {
                @Override
                public void onSuccess(Player player) {
                    mPlayerId = player.getPlayerId();

                    switchToMainScreen();
                }
            }).addOnFailureListener(createFailureListener("There was a problem getting the player id!"));
        }

        // register listener so we are notified if we receive an invitation to play
        // while we are in the game
        mInvitationsClient.registerInvitationCallback(mInvitationCallback);

        // get the invitation from the connection hint
        // Retrieve the TurnBasedMatch from the connectionHint
        GamesClient gamesClient = Games.getGamesClient(MainActivity.this, googleSignInAccount);
        gamesClient.getActivationHint().addOnSuccessListener(new OnSuccessListener<Bundle>()
        {
            @Override
            public void onSuccess(Bundle hint) {
                if (hint != null) {
                    Invitation invitation =
                            hint.getParcelable(Multiplayer.EXTRA_INVITATION);

                    if (invitation != null && invitation.getInvitationId() != null) {
                        // retrieve and cache the invitation ID
                        //Log.d(TAG, "onConnected: connection hint has a room invite!");
                        acceptInviteToRoom(invitation.getInvitationId());
                    }
                }
            }
        }).addOnFailureListener(createFailureListener("There was a problem getting the activation hint!"));
    }

    private OnFailureListener createFailureListener(final String string)
    {
        return new OnFailureListener()
        {
            @Override
            public void onFailure(@NonNull Exception e)
            {
                handleException(e, string);
            }
        };
    }

    public void onDisconnected()
    {
        //Log.d(TAG, "onDisconnected()");

        mRealTimeMultiplayerClient = null;
        mInvitationsClient = null;

        switchToMainScreen();
    }

    private RoomStatusUpdateCallback mRoomStatusUpdateCallback = new RoomStatusUpdateCallback()
    {
        // Called when we are connected to the room. We're not ready to play yet! (maybe not everybody
        // is connected yet).
        @Override
        public void onConnectedToRoom(Room room)
        {
            //Log.d(TAG, "onConnectedToRoom.");

            //get participants and my ID:
            mParticipants = room.getParticipants();
            mMyId = room.getParticipantId(mPlayerId);

            // save room ID if its not initialized in onRoomCreated() so we can leave cleanly before the game starts.
            if (mRoomId == null)
            {
                mRoomId = room.getRoomId();
            }

            // print out the list of participants (for debug purposes)
            //Log.d(TAG, "Room ID: " + mRoomId);
            //Log.d(TAG, "My ID " + mMyId);
            //Log.d(TAG, "<< CONNECTED TO ROOM>>");
        }

        // Called when we get disconnected from the room. We return to the main screen.
        @Override
        public void onDisconnectedFromRoom(Room room)
        {
            mRoomId = null;
            mRoomConfig = null;
            showGameError();
        }


        // We treat most of the room update callbacks in the same way: we update our list of
        // participants and update the display. In a real game we would also have to check if that
        // change requires some action like removing the corresponding player avatar from the screen,
        // etc.
        @Override
        public void onPeerDeclined(Room room, @NonNull List<String> arg1)
        {
            updateRoom(room);
        }

        @Override
        public void onPeerInvitedToRoom(Room room, @NonNull List<String> arg1)
        {
            updateRoom(room);
        }

        @Override
        public void onP2PDisconnected(@NonNull String participant) { }

        @Override
        public void onP2PConnected(@NonNull String participant) { }

        @Override
        public void onPeerJoined(Room room, @NonNull List<String> arg1)
        {
            updateRoom(room);
        }

        @Override
        public void onPeerLeft(Room room, @NonNull List<String> peersWhoLeft)
        {
            updateRoom(room);
        }

        @Override
        public void onRoomAutoMatching(Room room)
        {
            updateRoom(room);
        }

        @Override
        public void onRoomConnecting(Room room)
        {
            updateRoom(room);
        }

        @Override
        public void onPeersConnected(Room room, @NonNull List<String> peers)
        {
            updateRoom(room);
        }

        @Override
        public void onPeersDisconnected(Room room, @NonNull List<String> peers)
        {
            updateRoom(room);
        }
    };

    // Show error message about game being cancelled and return to main screen.
    void showGameError()
    {
        displayError(getString(R.string.game_problem));

        switchToMainScreen();
    }

    private RoomUpdateCallback mRoomUpdateCallback = new RoomUpdateCallback()
    {
        // Called when room has been created
        @Override
        public void onRoomCreated(int statusCode, Room room)
        {
            //Log.d(TAG, "onRoomCreated(" + statusCode + ", " + room + ")");

            if (statusCode != GamesCallbackStatusCodes.OK)
            {
                //Log.e(TAG, "*** Error: onRoomCreated, status " + statusCode);
                showGameError();
                return;
            }

            // save room ID so we can leave cleanly before the game starts.
            mRoomId = room.getRoomId();

            // show the waiting room UI
            showWaitingRoom(room);
        }

        // Called when room is fully connected.
        @Override
        public void onRoomConnected(int statusCode, Room room)
        {
            //Log.d(TAG, "onRoomConnected(" + statusCode + ", " + room + ")");

            if (statusCode != GamesCallbackStatusCodes.OK)
            {
                //Log.e(TAG, "*** Error: onRoomConnected, status " + statusCode);
                showGameError();
                return;
            }

            updateRoom(room);
        }

        @Override
        public void onJoinedRoom(int statusCode, Room room)
        {
            //Log.d(TAG, "onJoinedRoom(" + statusCode + ", " + room + ")");

            if (statusCode != GamesCallbackStatusCodes.OK)
            {
                //Log.e(TAG, "*** Error: onRoomConnected, status " + statusCode);
                showGameError();
                return;
            }

            // show the waiting room UI
            showWaitingRoom(room);
        }

        // Called when we've successfully left the room (this happens a result of voluntarily leaving
        // via a call to leaveRoom(). If we get disconnected, we get onDisconnectedFromRoom()).
        @Override
        public void onLeftRoom(int statusCode, @NonNull String roomId)
        {
            // we have left the room; return to main screen.
            //Log.d(TAG, "onLeftRoom, code " + statusCode);
            switchToMainScreen();
        }
    };

    void updateRoom(Room room)
    {
        if (room != null)
        {
            mParticipants = room.getParticipants();
        }
    }

    Participant getOpponent()
    {
        for (Participant participant : mParticipants)
        {
            if (!participant.getParticipantId().equals(mMyId))
            {
                return participant;
            }
        }

        return null;
    }
}
