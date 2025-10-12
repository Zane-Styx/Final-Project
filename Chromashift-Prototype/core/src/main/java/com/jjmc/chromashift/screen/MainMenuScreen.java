package com.jjmc.chromashift.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.jjmc.chromashift.network.HostServer;
import com.jjmc.chromashift.network.GameClient;

import java.io.IOException;

public class MainMenuScreen implements Screen {

    private Stage stage;
    private Skin skin;
    private HostServer hostServer;
    private GameClient client;

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        // Load uiskin.json from assets
        skin = new Skin(Gdx.files.internal("uiskin.json"));

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        TextButton hostBtn = new TextButton("Host", skin);
        TextButton joinBtn = new TextButton("Join", skin);

        table.add(hostBtn).width(200).height(50).padBottom(10).row();
        table.add(joinBtn).width(200).height(50).padBottom(10).row();

        // Host button logic
        hostBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hostBtn.setDisabled(true);
                new Thread(() -> {
                    try {
                        hostServer = new HostServer();
                        hostServer.start();
                        System.out.println("Host started!");

                        client = new GameClient("LocalPlayer");
                        client.start();
                        client.connectAsync("127.0.0.1");
                    } catch (IOException e) {
                        e.printStackTrace();
                        Gdx.app.postRunnable(() -> hostBtn.setDisabled(false));
                    }
                }).start();
            }
        });

        // Join button logic
        joinBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Window joinWindow = new Window("Join Game", skin);
                joinWindow.setModal(true);
                joinWindow.setMovable(false);
                joinWindow.align(1);

                TextField ipField = new TextField("127.0.0.1", skin);
                TextButton connectBtn = new TextButton("Connect", skin);
                TextButton cancelBtn = new TextButton("Cancel", skin);

                joinWindow.add("Host IP:").pad(6);
                joinWindow.add(ipField).width(200).pad(6).row();
                joinWindow.add(connectBtn).width(100).pad(6);
                joinWindow.add(cancelBtn).width(100).pad(6).row();

                joinWindow.pack();
                joinWindow.setPosition((Gdx.graphics.getWidth() - joinWindow.getWidth()) / 2f,
                    (Gdx.graphics.getHeight() - joinWindow.getHeight()) / 2f);

                stage.addActor(joinWindow);

                connectBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        connectBtn.setDisabled(true);
                        new Thread(() -> {
                            try {
                                client = new GameClient("Player");
                                client.start();
                                client.connectAsync(ipField.getText().trim());
                                Gdx.app.postRunnable(joinWindow::remove);
                            } catch (IOException e) {
                                e.printStackTrace();
                                Gdx.app.postRunnable(() -> connectBtn.setDisabled(false));
                            }
                        }).start();
                    }
                });

                cancelBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        joinWindow.remove();
                    }
                });
            }
        });
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.12f, 0.16f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();

        // Process incoming network messages for client
        if (client != null) {
            client.consumeNetworkMessages();
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() { }

    @Override
    public void resume() { }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        if (hostServer != null) hostServer.stop();
        if (client != null) client.stop();
    }
}
