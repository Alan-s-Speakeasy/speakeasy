package ch.ddis.speakeasy.user


data class GroupDetails(val id: String, val name: String, val users: List<UserDetails>) {

    companion object {
        fun of(group: Group): GroupDetails {
            return GroupDetails(group.id.string, group.name, group.users.map(UserDetails.Companion::of))
        }
    }
}