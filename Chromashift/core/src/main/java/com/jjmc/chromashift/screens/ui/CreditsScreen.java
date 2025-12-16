package com.jjmc.chromashift.screens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.jjmc.chromashift.screens.ui.MainMenuScreen;
import com.chromashift.helper.UIHelper;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.chromashift.helper.SpriteFont;
import com.chromashift.helper.SpriteFontManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Credits screen: shows team members, project info, and cycles portraits
 * with a fade in/out at right-center. Back button at bottom center.
 */
public class CreditsScreen extends AbstractMenuScreen {
    private Stage stage;
    private Skin skin;

    // Portraits
    private Texture[] portraits;
    private int portraitIndex = 0;
    private float portraitTimer = 0f;
    private float portraitCycleTime = 3.0f; // seconds each portrait stays visible
    private float fadeDuration = 1.0f; // seconds for fade in/out

    // Sprite-based text for credits
    private SpriteFont font;
    private String[] creditLines;
    private float fontScale = 3f;
    private float titleScale = 5f; // bigger title for "Credits"

    @Override
    public void show() {
        super.show();
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));

        // Root table fills screen (centered layout)
        Table root = new Table(skin);
        root.setFillParent(true);
        root.defaults().pad(6);
        root.center();

        // Left column: we will draw sprite-font credits manually; keep a spacer
        Table left = new Table(skin);
        left.add(new Label(" ", skin)).expand();

        // Right column reserved for portraits (drawn manually for fade control)
        Table right = new Table(skin);
        right.add(new Label(" ", skin)).expand(); // spacer

        // Bottom row: Back button centered (using UIHelper)
        Actor back = UIHelper.createButton("Back", skin, new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new MainMenuScreen());
            }
        });

        // Layout: two columns (left text, right portraits), then bottom back
        root.add(left).expand().left().top();
        root.add(right).expand().right().top();
        root.row();
        root.add(back).colspan(2).padTop(20).center();

        stage.addActor(root);

        // Prepare sprite font and credit lines
        try {
            if (!SpriteFontManager.isLoaded("default")) {
                SpriteFontManager.load("default", "ui/ctm.uiskin.png");
            }
            font = SpriteFontManager.get("default");
        } catch (Exception ignored) {}

        creditLines = new String[] {
                "Credits",
                "Leader",
                "Alcazar, John Alex B.",
                "Members",
                "Pascual, John Christopher A.",
                "Quinay, Caleb Joshua B.",
                "Gesmundo, Mark Kenrick B.",
                "",
                "Made with LibGDX",
                "Chromashift — a fast-paced 2D action platformer with boss encounters, skills, and responsive controls."
        };

        // Load portraits
        portraits = new Texture[] {
            safeLoad("ui/credits/alex.jpg"),
            safeLoad("ui/credits/jc.jpg"),
            safeLoad("ui/credits/mark.jpg"),
            safeLoad("ui/credits/caleb.jpg")
        };
    }

    private Texture safeLoad(String path) {
        try {
            return new Texture(Gdx.files.internal(path));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void render(float delta) {
        clearAndDrawScene(delta);
        // Back on ESC
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            navigateBack();
        }
        stage.act(delta);
        stage.draw();

        // Draw cycling portrait with fade at right-center
        SpriteBatch batch = getBatch();
        if (batch != null) {
            batch.begin();
            drawCenteredCredits(batch);
            drawPortraitCycle(batch, delta);
            batch.end();
        }
    }

    private void drawCenteredCredits(SpriteBatch batch) {
        if (font == null || creditLines == null) return;
        float vw = stage.getViewport().getWorldWidth();
        float vh = stage.getViewport().getWorldHeight();

        // Reserve right side for portraits; center credits in left/middle
        float areaW = vw * 0.55f;
        float areaX = vw * 0.22f; // slightly right of exact center to balance

        // Prepare wrapped lines with per-line scale
        List<String> lines = new ArrayList<String>();
        List<Float> scales = new ArrayList<Float>();
        for (int i = 0; i < creditLines.length; i++) {
            String text = creditLines[i];
            float scale = (i == 0 && "Credits".equalsIgnoreCase(text)) ? titleScale : fontScale;
            List<String> wrapped = wrapText(text, areaW, scale);
            for (String w : wrapped) {
                lines.add(w);
                scales.add(scale);
            }
        }

        // Compute total height based on per-line height
        float totalH = 0f;
        for (Float s : scales) {
            totalH += font.getHeight(s) + 4f;
        }
        float startY = (vh / 2f) + (totalH / 2f);

        // Draw each wrapped line centered within area
        float y = startY;
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            float scale = scales.get(i);
            float lineH = font.getHeight(scale) + 4f;
            float textW = font.getWidth(text, scale);
            float drawX = areaX + (areaW - textW) / 2f;
            font.draw(batch, text, drawX, y, scale);
            y -= lineH;
        }
    }

    // Simple word-wrap using spaces based on measured width
    private List<String> wrapText(String text, float maxWidth, float scale) {
        List<String> result = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            String candidate = line.length() == 0 ? word : line.toString() + " " + word;
            float width = font.getWidth(candidate, scale);
            if (width <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                result.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            result.add(line.toString());
        }
        return result;
    }

    private void drawPortraitCycle(SpriteBatch batch, float delta) {
        if (portraits == null || portraits.length == 0) return;
        Texture current = portraits[portraitIndex % portraits.length];
        Texture next = portraits[(portraitIndex + 1) % portraits.length];

        portraitTimer += delta;
        if (portraitTimer >= portraitCycleTime) {
            portraitTimer -= portraitCycleTime;
            portraitIndex = (portraitIndex + 1) % portraits.length;
            current = portraits[portraitIndex];
            next = portraits[(portraitIndex + 1) % portraits.length];
        }

        // Compute alpha for fade in/out across the cycle window
        float alphaCurrent;
        float alphaNext;
        if (portraitTimer < fadeDuration) {
            // Fade in current
            alphaCurrent = portraitTimer / fadeDuration;
            alphaNext = 0f;
        } else if (portraitTimer > (portraitCycleTime - fadeDuration)) {
            // Fade out current while next fades in
            float t = (portraitTimer - (portraitCycleTime - fadeDuration)) / fadeDuration;
            alphaCurrent = 1f - t;
            alphaNext = t;
        } else {
            alphaCurrent = 1f;
            alphaNext = 0f;
        }

        // Top-right placement with margin
        float vw = stage.getViewport().getWorldWidth();
        float vh = stage.getViewport().getWorldHeight();
        float margin = vw * 0.03f;
        float targetW = vw * 0.25f;
        float targetH = vh * 0.35f;
        float drawX = vw - targetW - margin;
        float drawY = vh - targetH - margin;

        // Render current
        if (current != null && alphaCurrent > 0f) {
            batch.setColor(1f, 1f, 1f, alphaCurrent);
            batch.draw(current, drawX, drawY, targetW, targetH);
        }
        // Render next underneath or above based on desired effect
        if (next != null && alphaNext > 0f) {
            batch.setColor(1f, 1f, 1f, alphaNext);
            batch.draw(next, drawX, drawY, targetW, targetH);
        }

        // Reset batch color
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private SpriteBatch getBatch() {
        // Use Stage's batch for overlay portrait drawing
        return stage != null ? (SpriteBatch) stage.getBatch() : null;
    }

    private void navigateBack() {
        // Return to main menu directly
        try {
            ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new MainMenuScreen());
        } catch (Exception e) {
            com.badlogic.gdx.Gdx.app.log("CreditsScreen", "Failed to navigate to main menu: " + e.getMessage());
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
        if (portraits != null) {
            for (Texture t : portraits) if (t != null) t.dispose();
            portraits = null;
        }
    }
}
