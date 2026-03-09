package io.github.some_example_name;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.Iterator;

abstract class Entity {
    protected Rectangle rect;

    public Entity(float x, float y, float width, float height) {
        rect = new Rectangle(x, y, width, height);
    }

    public abstract void update(float delta);
    public abstract void draw(Batch batch);

    // CODE SMELL FIX: Feature Envy
    public boolean collidesWith(Entity other) {
        return rect.overlaps(other.rect);
    }
}

class Player extends Entity {
    private float gravity = -800f, velocityY = 0;
    private boolean isJumping = false, isCrouching = false;
    private float normalHeight = 100, crouchHeight = 60;
    private Animation<TextureRegion> runAnimation;
    private float stateTime = 0f;

    public Player(Animation<TextureRegion> animation) {
        super(100, 200, 60, 100);
        this.runAnimation = animation;
    }

    public void jump() {
        if (!isJumping && !isCrouching) {
            velocityY = 400;
            isJumping = true;
        }
    }

    public void crouch(boolean down) {
        if (!isJumping) {
            if (down && !isCrouching) {
                isCrouching = true;
                rect.height = crouchHeight;
            } else if (!down && isCrouching) {
                isCrouching = false;
                rect.height = normalHeight;
            }
        }
    }

    @Override
    public void update(float delta) {
        velocityY += gravity * delta;
        rect.y += velocityY * delta;

        if (rect.y <= 200) {
            rect.y = 200;
            velocityY = 0;
            isJumping = false;
        }

        stateTime += delta;
    }

    @Override
    public void draw(Batch batch) {
        TextureRegion frame = runAnimation.getKeyFrame(stateTime, true);
        batch.draw(frame, rect.x, rect.y, rect.width, rect.height);
    }
}

class Obstacle extends Entity {
    private Texture texture;

    public Obstacle(float x, float y, float width, float height, Texture texture) {
        super(x, y, width, height);
        this.texture = texture;
    }

    @Override
    public void update(float delta) {
        rect.x -= 300 * delta;
    }

    @Override
    public void draw(Batch batch) {
        batch.draw(texture, rect.x, rect.y, rect.width, rect.height);
    }

    public boolean isOffScreen() {
        return rect.x + rect.width < 0;
    }
}


// CODE SMELL FIX: Inappropriate Intimacy

class ObstacleManager {

    private Array<Obstacle> obstacles = new Array<>();
    private Texture obstacleTexture;
    private long lastObstacleTime;

    public ObstacleManager(Texture texture) {
        this.obstacleTexture = texture;
        spawnObstacle();
    }
//    SMELL FIX- Shortgun SUrgery

    private void spawnObstacle() {
        float y = Math.random() < 0.5 ? 200 : 280;
        obstacles.add(
            new Obstacle(Gdx.graphics.getWidth(), y, 50, 50, obstacleTexture)
        );

        lastObstacleTime = TimeUtils.nanoTime();
    }

    public boolean update(float delta, Player player) {
        if (TimeUtils.nanoTime() - lastObstacleTime > 1000000000) {
            spawnObstacle();
        }
        Iterator<Obstacle> iter = obstacles.iterator();
        while (iter.hasNext()) {
            Obstacle obs = iter.next();
            obs.update(delta);
            if (obs.isOffScreen()) {
                iter.remove();
            }
            if (obs.collidesWith(player)) {
                return true;
            }
        }
        return false;
    }

    public void draw(Batch batch) {

        for (Obstacle obs : obstacles) {
            obs.draw(batch);
        }
    }
}


// CODE SMELL FIX: Divergent Change

class ScoreManager {
    private int score;
    private float scoreTimer = 0f;
    public void update(float delta) {
        scoreTimer += delta;
        while (scoreTimer >= 1f) {
            score += 10;
            scoreTimer -= 1f;
        }
    }
    public int getScore() {
        return score;
    }

    //preventing message chain
    public void draw(Batch batch, BitmapFont font) {
        font.draw(batch,
            "Score: " + score,
            20,
            Gdx.graphics.getHeight() - 20);
    }
}

public class GameScreen implements Screen {
    private SpriteBatch batch;
    private Player player;

    /* GameScreen no longer directly manages obstacle list */
    private ObstacleManager obstacleManager;

    /* Score handled by separate class */
    private ScoreManager scoreManager;

    private Texture obstacleTexture;
    private Texture backgroundTexture;
    private float backgroundX = 0;
    private TextureRegion[] runFrames;
    private Animation<TextureRegion> runAnimation;
    private BitmapFont font;

    final Main game;

    public GameScreen(Main game) {
        this.game = game;
    }

    @Override
    public void show() {
        batch = new SpriteBatch();

        Texture runSheet = new Texture("running_boy_green.png");
        TextureRegion[][] tmp = TextureRegion.split(runSheet, runSheet.getWidth() / 3, runSheet.getHeight() / 2);
        runFrames = new TextureRegion[6];
        int index = 0;
        for (TextureRegion[] row : tmp)
            for (TextureRegion frame : row)
                runFrames[index++] = frame;

        runAnimation = new Animation<>(0.1f, runFrames);

        player = new Player(runAnimation);

        backgroundTexture = new Texture("Greenery_Back.png");
        obstacleTexture = new Texture("neon_rectangle.jpg");

        /* initialize managers */
        obstacleManager = new ObstacleManager(obstacleTexture);
        scoreManager = new ScoreManager();

        font = new BitmapFont();
        font.getData().setScale(2f);
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            player.jump();
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            player.crouch(true);
        } else {
            player.crouch(false);
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        handleInput();

        backgroundX -= 100 * delta;
        if (backgroundX <= -Gdx.graphics.getWidth()) {
            backgroundX = 0;
        }

        player.update(delta);

        /* ObstacleManager now controls obstacle update and collision */
        boolean collided = obstacleManager.update(delta, player);

        if (collided) {
            game.setScreen(new GameOverScreen(game, scoreManager.getScore()));
            dispose();
            return;
        }

        scoreManager.update(delta);

        batch.begin();
        batch.draw(backgroundTexture, backgroundX, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.draw(backgroundTexture, backgroundX + Gdx.graphics.getWidth(), 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        player.draw(batch);

        obstacleManager.draw(batch);

        scoreManager.draw(batch, font);

        batch.end();
    }

    @Override public void resize(int width, int height) {}
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        batch.dispose();
        obstacleTexture.dispose();
        backgroundTexture.dispose();
        font.dispose();
    }
}
