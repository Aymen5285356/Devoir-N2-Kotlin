package DevoirN1

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StockException(message: String) : Exception(message)

data class EvenementStock(val idProduit: Int, val type: String, val quantite: Int)

class Produit(val id: Int, val nom: String, var quantite: Int) {
    fun afficherDetails() {
        println("Produit #$id | $nom | Quantité: $quantite")
    }
}

class Stock {
    val produits = mutableMapOf<Int, Produit>()
    val evenements = MutableSharedFlow<EvenementStock>(extraBufferCapacity = 64)
    private val mutex = Mutex()

    fun ajouterProduit(produit: Produit) {
        produits[produit.id] = produit
        evenements.tryEmit(EvenementStock(produit.id, "AJOUT_PRODUIT", produit.quantite)) }

    suspend fun ajouterQuantite(idProduit: Int, quantite: Int) {
        delay(200)
        mutex.withLock {
            val produit = produits[idProduit] ?: throw StockException("Produit $idProduit introuvable")
            produit.quantite += quantite }
        evenements.emit(EvenementStock(idProduit, "AJOUT", quantite)) }

    suspend fun retirerQuantite(idProduit: Int, quantite: Int) {
        delay(200)
        mutex.withLock {
            val produit = produits[idProduit] ?: throw StockException("Produit $idProduit introuvable")
            if (produit.quantite < quantite) {
                throw StockException(
                    "Stock insuffisant pour ${produit.nom} (disponible: ${produit.quantite}, demandé: $quantite)"
                ) }
            produit.quantite -= quantite }
        evenements.emit(EvenementStock(idProduit, "RETRAIT", quantite)) }

    suspend fun estDisponible(idProduit: Int, quantite: Int): Boolean {
        return mutex.withLock {
            (produits[idProduit]?.quantite ?: 0) >= quantite } }

    fun afficherStock() {
        println("--- Stock actuel ---")
        for (produit in produits.values) {
            produit.afficherDetails() } } }

class Commande2(val idCommande: Int, val produits: List<Pair<Int, Int>>) {
    fun afficherCommande() {
        println("Commande #$idCommande")
        for ((idProduit, quantite) in produits) {
            println("   Produit ID: $idProduit | Quantité demandée: $quantite")
        }
    }
}

class GestionnaireCommandes(val stock: Stock) {
    val commandes = mutableListOf<Commande2>()

    suspend fun traiterCommande(commande: Commande2) {
        commandes.add(commande)
        commande.afficherCommande()
        for ((idProduit, quantite) in commande.produits) {
            if (!stock.estDisponible(idProduit, quantite)) {
                println("Commande #${commande.idCommande} refusée : produit " +
                        "$idProduit indisponible (quantité $quantite)")
                return } }
        try {
            for ((idProduit, quantite) in commande.produits) {
                stock.retirerQuantite(idProduit, quantite)
            }
            println("Commande #${commande.idCommande} traitée avec succès")
        } catch (e: StockException) {
            println("Commande #${commande.idCommande} refusée : ${e.message}") } }

    suspend fun gererCommandes(commandes: List<Commande2>) = coroutineScope {
        for (commande in commandes) {
            launch { traiterCommande(commande) } } } }

class Entrepot(val scope: CoroutineScope) {
    val stock = Stock()
    val gestionnaireCommandes = GestionnaireCommandes(stock)

    fun ajouterProduitAuStock(produit: Produit): Job {
        return scope.launch {
            stock.ajouterProduit(produit) } }

    fun retirerProduitDuStock(idProduit: Int, quantite: Int): Job {
        return scope.launch {
            try {
                stock.retirerQuantite(idProduit, quantite)
                println("Retrait de $quantite du produit $idProduit effectué")
            } catch (e: StockException) {
                println("Erreur : ${e.message}") } } }

    fun gererInventaire(): Job {
        return scope.launch {
            println("Inventaire en cours...")
            delay(500)
            stock.afficherStock()
            println("Inventaire terminé") } } }

fun main() = runBlocking<Unit> {
    val entrepot = Entrepot(this)
    val stock = entrepot.stock
    val inventaire = mutableMapOf<Int, Int>()

    val collecteur = launch(start = CoroutineStart.UNDISPATCHED) {
        stock.evenements.collect { evenement ->
            val actuel = inventaire[evenement.idProduit] ?: 0
            when (evenement.type) {
                "AJOUT_PRODUIT" -> inventaire[evenement.idProduit] = evenement.quantite
                "AJOUT" -> inventaire[evenement.idProduit] = actuel + evenement.quantite
                "RETRAIT" -> inventaire[evenement.idProduit] = actuel - evenement.quantite
            }
            println("[Flow] ${evenement.type} produit ${evenement.idProduit} " +
                    "x${evenement.quantite} -> $inventaire") } }

    println("=== 1. Ajout des produits ===")
    listOf(
        entrepot.ajouterProduitAuStock(Produit(1, "Laptop", 10)),
        entrepot.ajouterProduitAuStock(Produit(2, "Souris", 50)),
        entrepot.ajouterProduitAuStock(Produit(3, "Clavier", 30))
    ).joinAll()

    println("=== 2. Ajout et retrait asynchrones ===")
    stock.ajouterQuantite(1, 5)
    entrepot.retirerProduitDuStock(2, 20).join()
    entrepot.retirerProduitDuStock(3, 100).join()

    println("=== 3. Commandes en parallèle et inventaire ===")
    val inventaireJob = entrepot.gererInventaire()
    entrepot.gestionnaireCommandes.gererCommandes(
        listOf(
            Commande2(1, listOf(1 to 2, 2 to 5)),
            Commande2(2, listOf(3 to 10)),
            Commande2(3, listOf(1 to 100))
        )
    )
    inventaireJob.join()

    println("=== 4. Stock final ===")
    stock.afficherStock()

    delay(300)
    collecteur.cancel()
}