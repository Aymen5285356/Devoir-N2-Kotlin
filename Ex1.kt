package DevoirN1

import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

var prochainId = 1

class Commande1(val id: Int, val plats: List<String>, val total: Double) {
    fun afficherDetails() {
        println("Commande #$id | Plats: ${plats.joinToString(", ")} | Total: $total DH")
    }
}

class Serveur(val nom: String) {
    fun prendreCommande(plats: List<String>, prix: List<Double>): Commande1 {
        val commande = Commande1(prochainId++, plats, prix.sum())
        println("Serveur $nom a pris la commande #${commande.id}")
        return commande }

    fun afficherCommande(commandes: List<Commande1>) {
        println("Commandes de $nom :")
        for (commande in commandes) {
            commande.afficherDetails() } } }

class Cuisinier(val nom: String) {
    suspend fun preparerPlat(plat: String): String {
        println("$nom prépare $plat...")
        delay(1000)
        return "$plat prêt (par $nom)"
    }

    suspend fun preparerCommande(commande: Commande1): List<String> = coroutineScope {
        commande.plats.map { plat -> async { preparerPlat(plat) } }.awaitAll()
    }
}

class Cuisine(val cuisiniers: List<Cuisinier>) {
    suspend fun gererPreparationCommande(commande: Commande1): List<String> = coroutineScope {
        commande.plats.mapIndexed { index, plat ->
            val cuisinier = cuisiniers[index % cuisiniers.size]
            async { cuisinier.preparerPlat(plat) }
        }.awaitAll()
    }
}

class Caisse {
    private val paiements = mutableMapOf<Int, Job>()

    suspend fun traiterPaiement(commande: Commande1): Boolean {
        paiements[commande.id] = coroutineContext[Job]!!
        println("Paiement de la commande #${commande.id} en cours...")
        delay(1500)
        if (Random.nextInt(100) < 30) {
            throw Exception("Paiement refusé pour la commande #${commande.id}")
        }
        paiements.remove(commande.id)
        return true
    }

    fun annulerPaiement(commande: Commande1) {
        paiements[commande.id]?.cancel()
        paiements.remove(commande.id)
    }
}

class Restaurant(
    val serveurs: List<Serveur>,
    val cuisine: Cuisine,
    val caisse: Caisse
) {
    val commandes = mutableListOf<Commande1>()

    suspend fun prendreCommandeEtTraiter(
        serveur: Serveur, plats: List<String>, prix: List<Double>) {
        val commande = serveur.prendreCommande(plats, prix)
        commandes.add(commande)
        commande.afficherDetails()
        try {
            val resultat = cuisine.gererPreparationCommande(commande)
            println("Commande #${commande.id} prête : $resultat")
            if (caisse.traiterPaiement(commande)) {
                println("Commande #${commande.id} payée avec succès")
            }
        } catch (e: CancellationException) {
            println("Commande #${commande.id} annulée")
            throw e
        } catch (e: Exception) {
            println("Erreur : ${e.message}")
        } finally {
            commandes.remove(commande)
        }
    }

    fun afficherCommandesEnCours() {
        println("--- Commandes en cours ---")
        for (commande in commandes) {
            commande.afficherDetails()
        }
    }

    fun annulerCommande(id: Int) {
        val commande = commandes.find { it.id == id }
        if (commande != null) {
            caisse.annulerPaiement(commande)
        }
    }
}

fun main() = runBlocking<Unit> {
    val cuisine = Cuisine(listOf(Cuisinier("Ali"),
        Cuisinier("Sara"), Cuisinier("Omar")))
    val restaurant = Restaurant(listOf(Serveur("Youssef"),
        Serveur("Imane")), cuisine, Caisse())
    launch {
        restaurant.prendreCommandeEtTraiter(
            restaurant.serveurs[0],
            listOf("Pizza", "Salade"),
            listOf(60.0, 25.0)
        ) }
    launch {
        restaurant.prendreCommandeEtTraiter(
            restaurant.serveurs[1],
            listOf("Tajine", "Couscous", "Soupe"),
            listOf(70.0, 65.0, 20.0)
        ) }
    launch {
        restaurant.prendreCommandeEtTraiter(
            restaurant.serveurs[0],
            listOf("Burger"),
            listOf(45.0)
        ) }
    delay(500)
    restaurant.afficherCommandesEnCours()
    delay(1300)
    restaurant.annulerCommande(2) }