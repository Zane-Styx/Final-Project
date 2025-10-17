package com.jjmc.chromashift.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.jjmc.chromashift.ChromashiftGame;

public class MainMenuScreen implements Screen {

    private final ChromashiftGame game;
    private Stage stage;
    private Skin skin;

    private TextField nameField;
    private TextField ipField;

    public MainMenuScreen(ChromashiftGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        // ✅ Create a skin with solid color drawables (no external files)
        skin = new Skin();
        BitmapFont font = new BitmapFont();
        skin.add("default-font", font);

        // Solid texture generator
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture whiteTex = new Texture(pixmap);
        pixmap.dispose();
        skin.add("white", whiteTex);

        // Label style
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = font;
        labelStyle.fontColor = Color.WHITE;
        skin.add("default", labelStyle);

        // Button style
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = new TextureRegionDrawable(skin.getRegion("white"));
        buttonStyle.down = new TextureRegionDrawable(skin.getRegion("white"));
        buttonStyle.checked = new TextureRegionDrawable(skin.getRegion("white"));
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.BLACK;
        skin.add("default", buttonStyle);

        // Text field style
        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = font;
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.cursor = new TextureRegionDrawable(skin.getRegion("white"));
        textFieldStyle.background = new TextureRegionDrawable(skin.getRegion("white"));
        textFieldStyle.background.setLeftWidth(5);
        textFieldStyle.background.setRightWidth(5);
        textFieldStyle.background.setTopHeight(5);
        textFieldStyle.background.setBottomHeight(5);
        textFieldStyle.cursor.setMinWidth(2);
        skin.add("default", textFieldStyle);

        // Layout container for left-aligned UI
        Table table = new Table();
        table.setFillParent(true);
        table.align(Align.left | Align.center);
        table.padLeft(80);
        stage.addActor(table);

        Label title = new Label("CHROMASHIFT", skin);
        title.setFontScale(2f);

        nameField = new TextField("Player", skin);
        ipField = new TextField("localhost", skin);

        TextButton hostButton = new TextButton("Host Game", skin);
        TextButton joinButton = new TextButton("Join Game", skin);
        TextButton exitButton = new TextButton("Exit", skin);

        // Layout
        table.add(title).padBottom(40f).row();
        table.add(new Label("Name:", skin)).left();
        table.row();
        table.add(nameField).width(200).padBottom(20f).row();
        table.add(new Label("Host IP:", skin)).left();
        table.row();
        table.add(ipField).width(200).padBottom(40f).row();
        table.add(hostButton).width(200).height(40).padBottom(20f).row();
        table.add(joinButton).width(200).height(40).padBottom(20f).row();
        table.add(exitButton).width(200).height(40).row();

        // Button actions
        hostButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String name = nameField.getText().isEmpty() ? "HostPlayer" : nameField.getText();
                // Use the injected ChromashiftGame instance to switch screens
                Gdx.app.postRunnable(() -> game.setScreen(new FirstScreen(true, "localhost", name)));
            }
        });

        joinButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String name = nameField.getText().isEmpty() ? "Player" : nameField.getText();
                String host = ipField.getText().isEmpty() ? "localhost" : ipField.getText();
                // Use the injected ChromashiftGame instance to switch screens
                Gdx.app.postRunnable(() -> game.setScreen(new FirstScreen(false, host, name)));
            }
        });

        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }
}
