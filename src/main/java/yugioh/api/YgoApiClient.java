package yugioh.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import yugioh.model.Card;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Cliente de la API REST de YGOProDeck.
 *
 * <ul>
 *   <li>Usa únicamente {@link java.net.http.HttpClient} (JDK 11+) y {@code org.json}.</li>
 *   <li><b>Todos los métodos públicos son BLOQUEANTES</b>: deben invocarse desde un hilo de
 *       fondo (p. ej. {@code SwingWorker.doInBackground()}), jamás desde el EDT.</li>
 *   <li>Garantiza que cada carta devuelta es de tipo "Monster": si la API entrega otra
 *       cosa (Spell, Trap, Skill, Token...) se repite la petición.</li>
 *   <li>Las cartas de un mismo lote se piden en paralelo para reducir el tiempo de espera.</li>
 * </ul>
 */
public class YgoApiClient {

    /** Endpoint obligatorio según el enunciado. */
    public static final String RANDOM_CARD_URL = "https://db.ygoprodeck.com/api/v7/randomcard.php";

    /** Máximo de peticiones por carta antes de rendirse (evita bucles infinitos). */
    static final int MAX_ATTEMPTS_PER_CARD = 40;
    /** Reintentos extra ante fallos transitorios de red/HTTP en una misma petición. */
    static final int MAX_TRANSIENT_RETRIES = 2;
    /** Descargas simultáneas máximas (la API permite 20 peticiones/segundo). */
    private static final int MAX_PARALLEL = 6;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "YugiohDuelLite/1.0 (laboratorio universitario)";

    private final HttpClient http;
    private final URI randomCardUri;

    public YgoApiClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                URI.create(RANDOM_CARD_URL));
    }

    /** Constructor para pruebas: permite inyectar un cliente y un endpoint falso. */
    public YgoApiClient(HttpClient http, URI randomCardUri) {
        this.http = http;
        this.randomCardUri = randomCardUri;
    }

    // ------------------------------------------------------------------
    // API pública (bloqueante)
    // ------------------------------------------------------------------

    /**
     * Obtiene {@code count} cartas Monster distintas, ya con su imagen descargada.
     * Las cartas se descargan en paralelo; la lista devuelta sigue el orden de llegada.
     *
     * @param onCardLoaded se invoca desde un hilo de descarga cada vez que una carta queda
     *                     completa (datos + imagen); puede ser {@code null}. Debe ser
     *                     seguro entre hilos (p. ej. {@code SwingWorker.publish}).
     * @throws ApiException si falla la red o no se logra obtener una carta válida. Si una
     *                      descarga falla, las demás se cancelan.
     */
    public List<Card> fetchMonsters(int count, Consumer<Card> onCardLoaded) throws ApiException {
        Set<Integer> seenIds = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(count, MAX_PARALLEL)), r -> {
            Thread t = new Thread(r, "ygo-fetch");
            t.setDaemon(true);
            return t;
        });
        try {
            CompletionService<Card> completion = new ExecutorCompletionService<>(pool);
            List<Future<Card>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(completion.submit(() -> {
                    Card card = fetchRandomMonsterWithImage(seenIds);
                    if (onCardLoaded != null) {
                        onCardLoaded.accept(card);
                    }
                    return card;
                }));
            }
            List<Card> cards = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                cards.add(completion.take().get());
            }
            return cards;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ApiException.Kind.NETWORK, "operación cancelada", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApiException) {
                throw (ApiException) cause;
            }
            throw new ApiException(ApiException.Kind.CARD_LOAD, String.valueOf(cause), cause);
        } finally {
            pool.shutdownNow();                                // cancela descargas pendientes
        }
    }

    /**
     * Pide cartas aleatorias hasta obtener un Monster utilizable y descarga su imagen.
     * El reintento se implementa con un bucle acotado (equivalente al ciclo recursivo
     * pedido, pero sin riesgo de desbordar la pila).
     *
     * @param seenIds ids ya reservados por otras cartas del duelo; se reserva el id de la
     *                carta elegida de forma atómica para no repetir cartas.
     */
    Card fetchRandomMonsterWithImage(Set<Integer> seenIds) throws ApiException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_CARD; attempt++) {
            String body = httpGetWithRetries(randomCardUri);
            Card parsed = parseMonster(body);                 // null => no es Monster / datos incompletos
            if (parsed == null || !seenIds.add(parsed.getId())) {
                continue;                                     // volver a solicitar
            }
            byte[] imageBytes = httpGetBytesWithRetries(URI.create(parsed.getImageUrl()));
            BufferedImage image = decodeImage(imageBytes);
            if (image == null) {
                continue;                                     // imagen ilegible: otra carta
            }
            return parsed.withImage(image);
        }
        throw new ApiException(ApiException.Kind.CARD_LOAD,
                "tras " + MAX_ATTEMPTS_PER_CARD + " intentos no se obtuvo una carta Monster válida");
    }

    // ------------------------------------------------------------------
    // Parseo JSON (visible en el paquete para pruebas)
    // ------------------------------------------------------------------

    /**
     * Convierte la respuesta JSON en una {@link Card} <b>sin imagen</b>.
     *
     * @return la carta, o {@code null} si NO es de tipo Monster o carece de datos
     *         imprescindibles (ATK o URL de imagen).
     * @throws ApiException si el JSON es inválido.
     */
    static Card parseMonster(String json) throws ApiException {
        try {
            JSONArray data = new JSONObject(json).getJSONArray("data");
            if (data.isEmpty()) {
                return null;
            }
            JSONObject o = data.getJSONObject(0);

            String type = o.optString("type", "");
            // Tipos reales: "Normal Monster", "Effect Monster", "Link Monster", "XYZ Monster"...
            if (!type.toLowerCase(Locale.ROOT).contains("monster")) {
                return null;
            }
            if (!o.has("atk") || o.isNull("atk")) {
                return null;                                  // sin ATK no se puede combatir
            }
            JSONArray images = o.optJSONArray("card_images");
            if (images == null || images.isEmpty()) {
                return null;
            }
            JSONObject img = images.getJSONObject(0);
            String url = img.optString("image_url_small", "");
            if (url.isEmpty()) {
                url = img.optString("image_url", "");
            }
            if (url.isEmpty()) {
                return null;
            }
            boolean hasDef = o.has("def") && !o.isNull("def");   // Link Monsters no tienen DEF
            return new Card(
                    o.getInt("id"),
                    o.getString("name"),
                    o.getInt("atk"),
                    hasDef ? o.getInt("def") : 0,
                    hasDef,
                    o.optString("frameType", "normal"),
                    url,
                    null);
        } catch (JSONException e) {
            throw new ApiException(ApiException.Kind.CARD_LOAD, "respuesta JSON inválida", e);
        }
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private String httpGetWithRetries(URI uri) throws ApiException {
        HttpResponse<String> resp = sendWithRetries(uri, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private byte[] httpGetBytesWithRetries(URI uri) throws ApiException {
        return sendWithRetries(uri, HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    /** GET con reintentos ante errores transitorios (IO, 429, 5xx). */
    private <T> HttpResponse<T> sendWithRetries(URI uri, HttpResponse.BodyHandler<T> handler)
            throws ApiException {
        ApiException last = null;
        for (int i = 0; i <= MAX_TRANSIENT_RETRIES; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ApiException(ApiException.Kind.NETWORK, "operación cancelada");
            }
            try {
                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(TIMEOUT)
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();
                HttpResponse<T> resp = http.send(req, handler);
                int status = resp.statusCode();
                if (status == 200) {
                    return resp;
                }
                last = new ApiException(ApiException.Kind.NETWORK, "el servidor respondió HTTP " + status);
                if (status != 429 && status < 500) {
                    throw last;                               // 4xx no transitorio: no insistir
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(ApiException.Kind.NETWORK, "operación cancelada", e);
            } catch (IOException e) {
                last = new ApiException(ApiException.Kind.NETWORK,
                        "no se pudo conectar con db.ygoprodeck.com (" + e.getClass().getSimpleName() + ")", e);
            }
            if (i < MAX_TRANSIENT_RETRIES) {
                pause(400L * (i + 1));                        // espera creciente antes de reintentar
            }
        }
        throw last;
    }

    private static void pause(long millis) throws ApiException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ApiException.Kind.NETWORK, "operación cancelada", e);
        }
    }

    private static BufferedImage decodeImage(byte[] bytes) throws ApiException {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new ApiException(ApiException.Kind.CARD_LOAD, "imagen ilegible", e);
        }
    }
}
