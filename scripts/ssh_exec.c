/* Android lifecycle wrapper: listener exits if its VISORA parent process dies. */
#include <sys/prctl.h>
#include <signal.h>
#include <unistd.h>
#include <stdio.h>
int main(int argc, char **argv) {
    if (argc < 2 || prctl(PR_SET_PDEATHSIG, SIGTERM) != 0 || getppid() == 1) return 125;
    execv(argv[1], argv+1);
    perror("VISORA SSH exec");
    return 126;
}
